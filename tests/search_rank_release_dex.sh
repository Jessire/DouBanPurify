#!/bin/sh
# Check the packaged APK, not the pre-R8 classes used by testReleaseUnitTest.
set -eu
apk=${1:?Usage: search_rank_release_dex.sh path/to/minified-release.apk}
dump=$(mktemp)
trap 'rm -f "$dump"' EXIT
"${DEXDUMP:-dexdump}" -d "$apk" > "$dump"
awk '
    /Class descriptor/ { cls = ($0 ~ /Lcom\/douban\/pure\/SearchRankLimit;/); install=0 }
    cls && /name[[:space:]]*:/ { install=($0 ~ /\047install\047/) }
    install && /const-string.*"kotlin.Unit"/ { unit=NR }
    install && unit && /ClassLoader;\.loadClass:/ && !load { load=NR }
    install && /XposedInterface;\.deoptimize:/ { deopt=NR }
    install && deopt && /XposedInterface;\.hook:/ { hook=NR }
    END {
        if (!(unit && unit<load && load<deopt && deopt<hook)) {
            print "FAIL: R8 APK is missing the host Unit lookup or scoped native bind Hook" > "/dev/stderr"
            exit 1
        }
        print "PASS: host Unit lookup and native bind deoptimize/Hook retained in DEX"
    }
' "$dump"
