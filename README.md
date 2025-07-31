# About
My template for Minecraft Fabric client-side mods. The easiest way to use this is to click `Use this template` and GitHub Actions will take care of the rest for you.

However, if you are using a Linux-based operating system, it is possible to clone this repository, and perform a refactor by triggering the `init.sh` script like so:
```shell
./init.sh <owner> <mod_name> 
```

Where `<owner>` is your GitHub username and `<mod_name>` is your GitHub repository name/mod name.

This script is designed to work both with GitHub Actions and manual usage, and will safely delete:
  - Leftover unused folders that are not tracked by Git (src/main/java/com/example and src/main/resources/examplemod).
  - The `init` workflow and script after successful execution.

# Credits
Thank you to [nea89o](https://github.com/nea89o)
for developing the GitHub Actions [workflow](https://github.com/nea89o/Forge1.8.9Template/blob/master/.github/workflows/init.yml)
and [script](https://github.com/nea89o/Forge1.8.9Template/blob/master/make-my-own.sh)
from which I based my workflow and script off of.