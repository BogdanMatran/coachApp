#!/usr/bin/env bash
# Mirrors the exercise thumbnails and animations into this deployment's `exercise-media` bucket.
#
#   scripts/sync-exercise-media.sh                      # local stack (npx supabase start)
#   SUPABASE_URL=https://xyz.supabase.co \
#   SUPABASE_SERVICE_ROLE_KEY=... scripts/sync-exercise-media.sh
#
# About 140 MB over ~2,650 objects; run it once per deployment. Nothing is downloaded into the
# repository and nothing is committed.
#
# The media is third-party content: the upstream dataset attributes it to (c) Gym visual
# (https://gymvisual.com/, terms at https://gymvisual.com/content/3-terms-and-conditions-of-use)
# under a permission granted to that dataset, while ExerciseDB/AscendAPI claims it as its own
# (https://exercisedb.io/faq). The two claims contradict each other and the ownership is
# unresolved. Read NOTICE.md before mirroring it anywhere you publish.

set -euo pipefail

DATASET_REPO="https://github.com/hasaneyldrm/exercises-dataset"
# Pinned to the same commit openGym uses, so filenames match the seeded image_path/gif_path.
DATASET_REF="7455efae41b330c265e7cd4b78dfa848e7ce5ebd"
BUCKET="exercise-media"

SUPABASE_URL="${SUPABASE_URL:-http://127.0.0.1:54321}"
# The default is the well-known local service-role key printed by `npx supabase start`.
SUPABASE_SERVICE_ROLE_KEY="${SUPABASE_SERVICE_ROLE_KEY:-}"

if [ -z "$SUPABASE_SERVICE_ROLE_KEY" ]; then
    echo "SUPABASE_SERVICE_ROLE_KEY is not set." >&2
    echo "For the local stack: export SUPABASE_SERVICE_ROLE_KEY=\$(npx supabase status -o env | grep SERVICE_ROLE_KEY | cut -d= -f2- | tr -d '\"')" >&2
    exit 1
fi

echo "Exercise media"
echo "  Source:  $DATASET_REPO @ ${DATASET_REF:0:7}"
echo "  Target:  $SUPABASE_URL/storage/v1/object/$BUCKET"
echo "  Rights:  (c) Gym visual / ExerciseDB -- unresolved, see NOTICE.md"
echo

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# A shallow clone of the pinned commit: one pass for ~140 MB. A blobless partial clone looks
# cheaper but then refetches every file individually on checkout, which is far slower.
echo "Fetching dataset (~140 MB)..."
git -c advice.detachedHead=false clone --quiet --depth 1 --branch main "$DATASET_REPO" "$tmp/ds"
git -C "$tmp/ds" fetch --quiet --depth 1 origin "$DATASET_REF"
git -C "$tmp/ds" -c advice.detachedHead=false checkout --quiet FETCH_HEAD

upload_dir() {
    local dir="$1" prefix="$2" mime="$3" count=0 total
    total="$(find "$dir" -type f | wc -l | tr -d ' ')"
    echo "Uploading $total files to $prefix/ ..."
    while IFS= read -r file; do
        count=$((count + 1))
        curl --silent --show-error --fail-with-body \
            -X POST "$SUPABASE_URL/storage/v1/object/$BUCKET/$prefix/$(basename "$file")" \
            -H "Authorization: Bearer $SUPABASE_SERVICE_ROLE_KEY" \
            -H "Content-Type: $mime" \
            -H "Cache-Control: public, max-age=31536000, immutable" \
            -H "x-upsert: true" \
            --data-binary "@$file" > /dev/null
        if [ $((count % 100)) -eq 0 ]; then echo "  $count / $total"; fi
    done < <(find "$dir" -type f)
    echo "  $count / $total"
}

upload_dir "$tmp/ds/images" img "image/jpeg"
upload_dir "$tmp/ds/videos" gif "image/gif"

echo
echo "Done. Spot check:"
echo "  curl -I $SUPABASE_URL/storage/v1/object/public/$BUCKET/img/0001-2gPfomN.jpg"
