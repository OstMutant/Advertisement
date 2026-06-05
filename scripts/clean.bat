@echo off
setlocal
cd /d "%~dp0.."

set ROOT=%CD%\

echo Cleaning Maven build artifacts...
for %%d in (
    marketplace-app\target
    platform-commons\target
    attachment-spring-boot-starter\target
    audit-spring-boot-starter\target
    taxon-spring-boot-starter\target
    query-starter\target
) do (
    if exist "%ROOT%%%d" (
        rmdir /s /q "%ROOT%%%d"
        echo   Removed %%d
    )
)

echo Cleaning Vaadin frontend generated files...
for %%f in (
    marketplace-app\src\main\frontend\generated
    marketplace-app\node_modules
    marketplace-app\package.json
    marketplace-app\package-lock.json
    marketplace-app\tsconfig.json
    marketplace-app\types.d.ts
    marketplace-app\vite.config.ts
    marketplace-app\vite.generated.ts
    marketplace-app\src\main\frontend\index.html
    marketplace-app\src\main\bundles
) do (
    if exist "%ROOT%%%f" (
        if exist "%ROOT%%%f\" (
            rmdir /s /q "%ROOT%%%f"
        ) else (
            del /q "%ROOT%%%f"
        )
        echo   Removed %%f
    )
)

echo Cleaning Playwright artifacts...
for %%d in (playwright\pw-report playwright\screenshots) do (
    if exist "%ROOT%%%d" (
        rmdir /s /q "%ROOT%%%d"
        echo   Removed %%d
    )
)

echo Done.
endlocal
