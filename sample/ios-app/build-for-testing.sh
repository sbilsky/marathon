#!/usr/bin/env bash

usage() {
  echo 1>&2 -e "Usage: bash" "${BASH_SOURCE##*/} <Simulator UDID | Simulator name>"
  echo 1>&2
  echo 1>&2 -e "To list available simulators, run $(tput bold)xcrun simctl list devices$(tput sgr0)"
  echo 1>&2
}

cd "$(cd "$(dirname "$0")" && pwd)"

get_available_udid_by_name() {
  if command -v jq 2>&1 1>/dev/null; then
    xcrun --sdk iphonesimulator simctl list devices -j | jq -r '.devices | flatten | .[] | select(.isAvailable == true) | select(.name == "'"$1"'") | .udid' | head -1
  fi
}

if ! XCODEBUILD="$(command -v xcodebuild)"; then
  echo 1>&2 -e "$(tput setaf 1)ERROR: $(tput bold)xcodebuild$(tput sgr0) $(tput setaf 1)not found$(tput sgr0)"
  exit 1
fi

if [[ -z $1 ]]; then
  echo 1>&2 -e "$(tput setaf 1)ERROR: Required iOS simulator information is missing.$(tput sgr0)"
  echo 1>&2
  usage
  exit 1
fi

if [[ -n $1 ]]; then
  if [[ $1 =~ ^\{?[A-F0-9a-f]{8}-[A-F0-9a-f]{4}-[A-F0-9a-f]{4}-[A-F0-9a-f]{4}-[A-F0-9a-f]{12}\}?$ ]]; then
    XCODEBUILD_DESTINATION="$1"
  else
    XCODEBUILD_DESTINATION="$(get_available_udid_by_name "$1")"
  fi
fi

echo -e "Building for device $(tput bold)${XCODEBUILD_DESTINATION}$(tput sgr0)"

$XCODEBUILD build-for-testing -derivedDataPath derived-data -workspace sample-app.xcworkspace -scheme UITesting -sdk iphonesimulator -destination "platform=iOS Simulator,id=$XCODEBUILD_DESTINATION" ENABLE_ONLY_ACTIVE_RESOURCES=NO
