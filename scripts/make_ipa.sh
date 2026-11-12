#!/bin/bash
# Kitae for iPhone
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Builds an unsigned IPA of the current commit, for sideloading with AltStore, SideStore or
# Sideloadly, which sign it with the installer's own Apple Account:
#
#   scripts/make_ipa.sh [BUILD]
#
# BUILD is the build number to give it, normally the project's own (leave it out). The IPA goes
# into build/ipa/ as Kitae-VERSION-BUILD.ipa, with its SHA-256 beside it. Needs Xcode; nothing
# is signed or uploaded. Kitae has no entitlements, so an unsigned copy loses nothing.
set -euo pipefail
cd "$(dirname "$0")/.."

build="${1:-}"
if [ -n "$build" ] && ! [[ "$build" =~ ^[0-9]{1,6}$ ]]; then
    echo "The build number must be a number, like 2." >&2
    exit 2
fi
if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "There are uncommitted changes. Commit them first, so the IPA matches a commit." >&2
    exit 1
fi

out="build/ipa"
archive="$out/Kitae.xcarchive"
rm -rf "$archive" "$out/Payload"
mkdir -p "$out"

args=(-project ios/Kitae.xcodeproj -scheme Kitae -configuration Release
      -destination "generic/platform=iOS" -archivePath "$archive"
      CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" PROVISIONING_PROFILE_SPECIFIER="")
if [ -n "$build" ]; then
    args+=(CURRENT_PROJECT_VERSION="$build")
fi
xcodebuild "${args[@]}" -quiet archive

app="$archive/Products/Applications/Kitae.app"
version=$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "$app/Info.plist")
number=$(/usr/libexec/PlistBuddy -c "Print :CFBundleVersion" "$app/Info.plist")
ipa="Kitae-$version-$number.ipa"

mkdir -p "$out/Payload"
ditto "$app" "$out/Payload/Kitae.app"
rm -f "$out/$ipa" "$out/$ipa.sha256"
(cd "$out" && zip -qry "$ipa" Payload && shasum -a 256 "$ipa" > "$ipa.sha256")
rm -rf "$out/Payload" "$archive"

echo "Built $out/$ipa ($(du -h "$out/$ipa" | cut -f1)) from $(git rev-parse --short HEAD), version $version ($number)"
cat "$out/$ipa.sha256"
