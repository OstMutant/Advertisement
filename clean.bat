@echo off
setlocal

set ROOT=%~dp0

echo Cleaning Maven build artifacts...
for %%d in (
    advertisement-app\target
    advertisement-contracts\target
    attachment-spring-boot-starter\target
    audit-spring-boot-starter\target
    sql-engine\target
) do (
    if exist "%ROOT%%%d" (
        rmdir /s /q "%ROOT%%%d"
        echo   Removed %%d
    )
)

echo Cleaning Vaadin frontend generated files...
for %%f in (
    advertisement-app\src\main\frontend\generated
    advertisement-app\node_modules
    advertisement-app\package.json
    advertisement-app\package-lock.json
    advertisement-app\tsconfig.json
    advertisement-app\types.d.ts
    advertisement-app\vite.config.ts
    advertisement-app\vite.generated.ts
    advertisement-app\src\main\frontend\index.html
    advertisement-app\src\main\bundles
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
