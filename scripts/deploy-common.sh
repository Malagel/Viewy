#!/usr/bin/env bash
set -euo pipefail

# Sourced only by the two fixed build-type entry points.
case "$deploy_variant" in
    debug)
        app_id='com.malagel.viewy.debug'
        app_label='ViewyDebug'
        assemble_task=':app:assembleDebug'
        build_options=() ;;
    release)
        app_id='com.malagel.viewy'
        app_label='Viewy'
        assemble_task=':app:assembleRelease'
        build_options=(-Pviewy.localReleaseSigning=true) ;;
    *) printf 'Error: Unsupported build type.\n' >&2; exit 1 ;;
esac

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

wireless_help() {
    cat >&2 <<'EOF'
On the phone, enable Developer options > Wireless debugging.
If pairing is needed: adb pair PHONE_IP:PAIRING_PORT
Then connect:         adb connect PHONE_IP:DEBUGGING_PORT
Check the connection: adb devices -l
Use the current addresses shown on the phone; pairing and debugging ports differ.
EOF
    printf 'ADB executable: %s\n' "$adb" >&2
}

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
config_file="$repo_root/scripts/deploy.local.conf"
# Save explicit settings before loading the optional, trusted Bash configuration.
declare -A overrides=()
config_keys=(JAVA_HOME ANDROID_HOME ANDROID_SDK_ROOT ANDROID_USER_HOME GRADLE_USER_HOME PHONE_SERIAL ANDROID_SERIAL)
for key in "${config_keys[@]}"; do
    [[ ! -v "$key" ]] || overrides[$key]="${!key}"
done
if [[ -f "$config_file" ]]; then
    source "$config_file" || fail "Could not load $config_file. Check its Bash syntax."
fi
# An explicit SDK setting takes precedence over either local SDK alias.
if [[ -v overrides[ANDROID_HOME] || -v overrides[ANDROID_SDK_ROOT] ]]; then
    unset ANDROID_HOME ANDROID_SDK_ROOT
fi
for key in "${!overrides[@]}"; do
    printf -v "$key" '%s' "${overrides[$key]}"
done

[[ -n ${JAVA_HOME:-} ]] || fail "Set JAVA_HOME to a JDK (17 or newer) in the environment or $config_file."
export JAVA_HOME
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$ANDROID_HOME" ]] || fail "Set ANDROID_HOME (or ANDROID_SDK_ROOT) in the environment or $config_file."
if [[ -n ${ANDROID_SDK_ROOT:-} ]]; then
    [[ "$ANDROID_HOME" -ef "$ANDROID_SDK_ROOT" ]] ||
        fail 'ANDROID_HOME and ANDROID_SDK_ROOT must refer to the same SDK.'
    export ANDROID_SDK_ROOT
fi
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$HOME/.android}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

[[ -x "$JAVA_HOME/bin/java" && -x "$JAVA_HOME/bin/javac" ]] ||
    fail "JDK not found at $JAVA_HOME. Set JAVA_HOME to a valid JDK (17 or newer)."
[[ -d "$ANDROID_HOME" ]] || fail "Android SDK not found at $ANDROID_HOME. Set ANDROID_HOME."
[[ -x "$repo_root/gradlew" ]] || fail "Executable Gradle wrapper missing: $repo_root/gradlew"
adb="$(command -v adb)" || fail "adb not found. Check $ANDROID_HOME/platform-tools or PATH."

command -v timeout >/dev/null || fail 'timeout (GNU coreutils) is required for bounded ADB discovery.'

# Only this paired phone is selected automatically. ANDROID_SERIAL is an explicit override.
phone_serial="${PHONE_SERIAL:-}"
service_prefix="adb-$phone_serial-"
requested_target="${ANDROID_SERIAL:-}"
[[ -n "$phone_serial" || -n "$requested_target" ]] ||
    fail "Set PHONE_SERIAL to the phone's hardware serial in $config_file or the environment for automatic discovery, or set ANDROID_SERIAL to an authorized target from 'adb devices -l'."
target=''
deadline=$((SECONDS + 15))

# Bound both individual ADB calls and the entire reconnection period.
bounded_adb() {
    local remaining=$((deadline - SECONDS))
    (( remaining > 0 )) || return 1
    (( remaining <= 3 )) || remaining=3
    timeout --kill-after=1 "${remaining}s" "$adb" "$@"
}

select_target() {
    local serial state rest physical_serial
    # Prefer the known service transport, even if its IP transport is also listed.
    while read -r serial state rest; do
        [[ "$state" == device ]] || continue
        if [[ -n "$requested_target" ]]; then
            [[ "$serial" == "$requested_target" ]] || continue
        else
            [[ "$serial" == "$service_prefix"*._adb-tls-connect._tcp* ]] || continue
        fi
        if [[ "$(bounded_adb -s "$serial" get-state 2>/dev/null)" == device ]]; then
            target="$serial"
            return 0
        fi
    done <<< "$devices"

    [[ -z "$requested_target" ]] || return 1
    # When mDNS is empty, identify IP/USB transports by the phone's hardware serial.
    # Matching transports are aliases for this one phone, not additional devices.
    while read -r serial state rest; do
        [[ "$state" == device ]] || continue
        physical_serial="$(bounded_adb -s "$serial" shell -n getprop ro.serialno 2>/dev/null)" || continue
        if [[ "${physical_serial//$'\r'/}" == "$phone_serial" ]]; then
            target="$serial"
            return 0
        fi
    done <<< "$devices"
    return 1
}

printf 'Looking for %s (up to 15 seconds)...\n' "${requested_target:-$phone_serial}"
devices=''
while (( SECONDS < deadline )); do
    devices="$(bounded_adb devices -l 2>/dev/null)" || devices=''
    if select_target; then
        break
    fi

    # Connect only endpoints advertised for the configured phone's debugging service.
    # Pairing services and other phones are deliberately ignored.
    services="$(bounded_adb mdns services 2>/dev/null)" || services=''
    while read -r service type endpoint rest; do
        [[ -n "$phone_serial" ]] || continue
        [[ "$service" == "$service_prefix"* ]] || continue
        [[ "$type" == _adb-tls-connect._tcp || "$type" == _adb-tls-connect._tcp. ]] || continue
        [[ -n "$endpoint" && "$endpoint" != -* ]] || continue
        if [[ -n "$requested_target" && "$requested_target" != "$endpoint" &&
              "$requested_target" != "$service" &&
              "$requested_target" != "$service"._adb-tls-connect._tcp* ]]; then
            continue
        fi
        bounded_adb connect "$endpoint" >/dev/null 2>&1 || true
    done <<< "$services"

    # Recheck after connecting; also allows ADB's own delayed auto-connect to finish.
    devices="$(bounded_adb devices -l 2>/dev/null)" || devices=''
    if select_target; then
        break
    fi
    (( SECONDS < deadline )) && sleep 1
done

if [[ -z "$target" ]]; then
    printf '%s\n' "$devices" >&2
    wireless_help
    fail "Could not identify and reach ${requested_target:-$phone_serial} within 15 seconds. Check authorization/connection or set ANDROID_SERIAL to an authorized target."
fi

cd -- "$repo_root"
printf 'Building %s for %s...\n' "$app_label" "$target"
./gradlew --no-daemon --max-workers=1 --no-watch-fs "${build_options[@]}" "$assemble_task" || fail 'Build failed; deployment stopped.'
apk="$repo_root/app/build/outputs/apk/$deploy_variant/app-$deploy_variant.apk"
[[ -f "$apk" ]] || fail "Build completed but APK is missing: $apk"
install_output="$("$adb" -s "$target" install -r "$apk" 2>&1)" || {
    printf '%s\n' "$install_output" >&2
    if [[ "$install_output" == *INSTALL_FAILED_UPDATE_INCOMPATIBLE* ||
          "$install_output" == *INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES* ||
          "$install_output" == *signatures*do*not*match* ]]; then
        fail "Signing certificate mismatch for $app_id on $target. Rebuild with the certificate used by the installed app. Switching between development and production signing requires an explicit migration decision; no app was uninstalled and no data was cleared."
    fi
    fail "APK installation failed on $target; no app was uninstalled and no data was cleared."
}
printf '%s\n' "$install_output"
launch_output="$("$adb" -s "$target" shell am start -W -n "$app_id/com.malagel.viewy.MainActivity" 2>&1)" ||
    fail "Could not launch $app_label: $launch_output"
printf '%s\n' "$launch_output"
if [[ "$launch_output" == *Error:* || "$launch_output" == *Exception* ]]; then
    fail 'Android reported a launch error.'
fi
printf 'Success: %s built, installed, and launched on %s.\n' "$app_label" "$target"
