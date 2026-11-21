#!/usr/bin/env bash
# Uploads the signed release AAB for Words of Wisdom (com.cubeworks.wordsofwisdom)
# to Google Play Console's Internal Testing track via the Play Developer API.
#
# Usage:
#   tools/play_upload.sh --check
#   tools/play_upload.sh --tracks
#   tools/play_upload.sh --build [--status draft|completed] [--notes "Release notes"]
#   tools/play_upload.sh --aab path/to/app-release.aab [--track internal] [--status draft]
set -euo pipefail
cd "$(dirname "$0")/.."

key_file="${PLAY_KEY_FILE:-$HOME/.config/play/xoelevation-service-account.json}"
package="com.cubeworks.wordsofwisdom"
track="internal"
status="completed"
notes="Initial internal test build of Words of Wisdom (& Life Lessons) for Google TV & Android TV."
aab="app/build/outputs/bundle/release/app-release.aab"
do_build=false
do_check=false
do_tracks=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --track) track="$2"; shift 2 ;;
    --status) status="$2"; shift 2 ;;
    --notes) notes="$2"; shift 2 ;;
    --aab) aab="$2"; shift 2 ;;
    --build) do_build=true; shift ;;
    --check) do_check=true; shift ;;
    --tracks) do_tracks=true; shift ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

[[ -r "$key_file" ]] || { echo "Service account key not found: $key_file" >&2; exit 1; }

if $do_build; then
  ./gradlew :app:bundleRelease
fi

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

access_token() {
  local now header claims unsigned sig
  now=$(date +%s)
  header=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
  claims=$(jq -cn --arg iss "$(jq -r .client_email "$key_file")" --argjson now "$now" '{
    iss: $iss,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: $now, exp: ($now + 3600)
  }' | b64url)
  unsigned="$header.$claims"
  sig=$(printf '%s' "$unsigned" | openssl dgst -sha256 -sign <(jq -r .private_key "$key_file") | b64url)
  curl -sS --fail-with-body https://oauth2.googleapis.com/token \
    -d grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer \
    -d "assertion=$unsigned.$sig" | jq -r .access_token
}

token=$(access_token)
api="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$package"

play() {
  local body msg
  if body=$(curl -sS --fail-with-body -H @<(printf 'Authorization: Bearer %s' "$token") "$@"); then
    printf '%s\n' "$body"
  else
    msg=$(jq -r '.error.message // empty' <<<"$body" 2>/dev/null || true)
    echo "Play API error: ${msg:-$body}" >&2
    return 1
  fi
}

edit_json=$(play -X POST "$api/edits")
edit=$(jq -r .id <<<"$edit_json")

if $do_check || $do_tracks; then
  if $do_tracks; then
    play "$api/edits/$edit/tracks" | jq -r '.tracks[] | "  \(.track): " + ([.releases[]? | "\(.name // "-") \(.versionCodes // []) \(.status)"] | join(", "))'
  fi
  play -X DELETE "$api/edits/$edit" >/dev/null
  $do_check && echo "OK: Service account has Play Developer API access for $package."
  exit 0
fi

[[ -f "$aab" ]] || { echo "AAB not found: $aab" >&2; exit 1; }
if keytool -printcert -jarfile "$aab" | grep -q "CN=Android Debug"; then
  echo "Refusing upload: $aab is signed with the debug key!" >&2
  exit 1
fi

echo "Uploading $aab to $package ($track track, status=$status)..."
vc=$(play -X POST -H "Content-Type: application/octet-stream" \
  --data-binary @"$aab" \
  "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$package/edits/$edit/bundles?uploadType=media" \
  | jq -r .versionCode)

release=$(jq -cn --arg s "$status" --arg v "$vc" --arg n "$notes" \
  '{versionCodes: [$v], status: $s, releaseNotes: [{language: "en-US", text: $n}]}')
play -X PUT -H "Content-Type: application/json" \
  -d "$(jq -cn --arg t "$track" --argjson r "$release" '{track: $t, releases: [$r]}')" \
  "$api/edits/$edit/tracks/$track" >/dev/null

play -X POST "$api/edits/$edit:commit" >/dev/null
echo "Uploaded $package versionCode=$vc to track '$track' ($status)."
