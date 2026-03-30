# Android Studio templates (tools/base) – notes

This project historically generated “ACS-like” Android projects using a mix of:

- `app/src/main/assets/acs-templates/*` (base Gradle + wrappers)
- `app/src/main/assets/atc/resources/*` (base res/icons/themes)
- `app/src/main/assets/templates/{bottomNav,navDrawer,tabbed}/res/*` (template-specific `res/` copies)
- Kotlin/Java source generation in `app/src/main/java/com/termux/app/home/create/AndroidProjectGenerator.kt`

## Where “official” Android Studio templates live

For modern Android Studio, the templates are no longer stored as legacy Freemarker `templates/*`.
Instead they are implemented as Kotlin DSL “wizard templates” in **tools/base** under:

- `wizard/template-plugin` – template DSL + `RecipeExecutor` interfaces
- `wizard/template-impl` – actual templates and recipes
  - `wizard/template-impl/src/...` – Kotlin source generators + recipes
  - `wizard/template-impl/res/...` – template assets used by recipes (mostly thumbnails + some drawable resources)

For the pinned Studio tag used during investigation:

- tag: `studio-2024.3.2-patch01`
- commit used for archive downloads: `15d750b6b20b8073e6b1b5b478afb5fdd3ccb738`

## What we currently vendor from tools/base

### 1) Template preview images (UI only)

Copied from `wizard/template-impl/res/*/template_*.png` into our app resources:

- `app/src/main/res/drawable/*.png`
- `app/src/main/res/drawable-night/*.png` (dark previews)

This improves the “Create Project” template picker visuals without changing generation behavior.

### 2) A few template drawables (assets)

Some templates ship XML vector drawables under `wizard/template-impl/res/<template>/drawable*`.
We copy/update a subset into:

- `app/src/main/assets/templates/bottomNav/res/drawable/*`
- `app/src/main/assets/templates/navDrawer/res/drawable/*`

These are mainly icon vectors (formatting-only diffs vs our prior copies).

## Next integration step (not done yet)

To actually generate projects using “official” templates, we would need to either:

1. **Port a minimal recipe runner** (a `RecipeExecutor` implementation) and vendor the necessary
   `wizard/template-plugin` + `wizard/template-impl` code/resources, then call recipes directly.

or

2. Continue the current approach (copy `res/` from assets + generate sources manually), but
   periodically update our assets/sources to match tools/base.

Option (1) is closer to “official templates” but is a much larger dependency/architecture change.
