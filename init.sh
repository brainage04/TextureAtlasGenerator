#!/bin/bash
if [ "$#" -ne "2" ]; then
  echo "Usage: $0 <owner> <mod_name>"
  exit 0
fi

base=$(dirname "$(readlink -f "$0")")
echo "Updating $base"

owner="$1"
mod_name="$2"
package_name="com.github.${owner,,}.${mod_name,,}"
mod_id=${mod_name,,}
package_dir=$(echo "$package_name" | tr . /)
echo "Setting owner to $owner"
echo "Setting mod name to $mod_name"
echo "Setting package name to $package_name"
echo "Setting mod id to $mod_id"
echo "Setting package dir to $package_dir"

(
  # enable debug tracing
  set -x

  # refactor mod id, mod name, owner and package name strings
  # important that owner is done before package name
  # as owner may or may not be in the package name string
  # which will cause issues if replaced
  find "$base/src/main" -type f -exec sed -i \
      -e "s/examplemod/$mod_id/g" \
      -e "s/ExampleMod/$mod_name/g" \
      -e "s/brainage04/$owner/g" \
      -e "s/com\.example/$package_name/g" {} +

  sed -i \
        -e "s/examplemod/$mod_id/g" \
        -e "s/ExampleMod/$mod_name/g" \
        -e "s/brainage04/$owner/g" "$base/build.gradle"

  sed -i \
      -e "s/com\.example/$package_name/g" \
      -e "s/examplemod/$mod_id/g" "$base/gradle.properties"

  sed -i \
        -e "s/brainage04/$owner/g" "$base/LICENSE"

  # refactor accesswidener and mixin file names
  mv "$base"/src/main/resources/examplemod.accesswidener "$base"/src/main/resources/"$mod_id".accesswidener
  mv "$base"/src/main/resources/examplemod.mixins.json "$base"/src/main/resources/"$mod_id".mixins.json

  # refactor assets directory
  mv "$base"/src/main/resources/assets/examplemod "$base"/src/main/resources/assets/"$mod_id"

  # rename main class
  mv "$base"/src/main/java/com/example/ExampleMod.java "$base"/src/main/java/com/example/"$mod_name".java

  # lastly, refactor package directory
  mkdir -p "$base"/src/main/java/"$package_dir"
  mv "$base"/src/main/java/com/example/* "$base"/src/main/java/"$package_dir"
  rmdir "$base"/src/main/java/com/example
  rmdir "$base"/src/main/java/com

  rm "$base"/.github/workflows/init.yml
  rm "$(readlink -f "$0")"
)

echo "Refactor completed successfully"