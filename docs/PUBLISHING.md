# Publishing on CurseForge

GitHub Actions uploads every new version of the mod to CurseForge by itself. This page says how that works, how to set it up once, and what to do for each release.

## How it works

The `Build` workflow (`.github/workflows/build.yml`) builds the jar and runs the in-game tests on every push. On `main`, a second job, **Upload to CurseForge**, then runs:

1. It reads `mod_version` and `curseforge_project_id` from `gradle.properties`.
2. If the `CURSEFORGE_TOKEN` secret or the project id is missing, it stops with a note that CurseForge isn't set up.
3. If the tag `v<mod_version>` already exists, that version is already on CurseForge, and it stops.
4. Otherwise it takes the jar the tests just passed and runs `.github/scripts/curseforge_upload.py`. The script:
   - tags the file with Minecraft 1.21.1, NeoForge, Java 21, Client and Server;
   - uses the `## <version>` section of `CHANGELOG.md` as the changelog;
   - lists the optional mods it works with (Jade, WTHIT, JEI, EMI, JourneyMap, Xaero's Minimap);
   - uploads the jar as a release named "Reiko's Wireless Redstone <version>".
5. It pushes the tag `v<mod_version>`, so the same version is never uploaded twice.

If CurseForge refuses one of the optional mods' names, the script uploads again without that list and leaves a warning in the run. A new file shows as "Under review" on CurseForge until its moderators approve it.

## Setting it up (once)

The project exists: https://www.curseforge.com/minecraft/mc-mods/reikos-wireless-redstone (Project ID 1797165, already in `gradle.properties`). Steps 1, 2 and 5 are done; steps 3, 4 and 6 are left.

1. **Create the project.** Sign in to CurseForge and go to https://www.curseforge.com/project/create. Choose Minecraft and **Mods**, then fill in the page from `docs/curseforge/`:
   - **Name:** Reiko's Wireless Redstone.
   - **Summary:** `summary.txt`.
   - **Description:** `description.md` (or `description.html` in the editor's source view).
   - **Logo:** `docs/images/icon.png` (512 × 512).
   - **Categories:** Redstone, plus Technology and Utility & QoL.
   - **License:** All Rights Reserved, like the mod (`mod_license` in `gradle.properties`), unless you choose another.
   - **Source and issues:** https://github.com/Reiko45/Reiko-s-Wireless-Redstone.
2. **Note the project id.** It's the number under **Project ID** in the project page's About section.
3. **Make an upload token.** Go to https://authors.curseforge.com/#/settings/api-tokens, make a token (call it "GitHub Actions") and copy it. Don't paste it anywhere but GitHub.
4. **Give the token to GitHub.** On the repository, go to **Settings → Secrets and variables → Actions → New repository secret**. Name it `CURSEFORGE_TOKEN` and paste the token as its value.
5. **Put the project id in `gradle.properties`** (`curseforge_project_id=123456`) and push. That run uploads the current version.
6. **Add the screenshots** on the project's Images tab, in the order and with the titles in `docs/curseforge/gallery.md`.

## For each release

1. Raise `mod_version` in `gradle.properties`.
2. Add a `## <version>` section at the top of `CHANGELOG.md`. Without one the upload stops, so add it before pushing.
3. Push to `main`. Once the build and tests pass, the new version goes to CurseForge.

To try an upload again (after fixing the token, say), run the `Build` workflow by hand from the Actions tab. It skips any version that's already tagged.

## If something goes wrong

- **"CurseForge isn't set up yet":** the `CURSEFORGE_TOKEN` secret or `curseforge_project_id` is missing.
- **"CurseForge refused the token":** the token was deleted or mistyped. Make a new one and update the secret.
- **"has no '## x.y.z' section":** add that version's section to `CHANGELOG.md` and push.
- **To upload a version again:** delete its file on CurseForge and its `v<version>` tag on GitHub, then run the workflow.

`python3 .github/scripts/curseforge_upload.py --jar <jar> --version <version> --project-id <id> --dry-run` shows what would be uploaded without sending anything.
