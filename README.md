# FootballStats Android

Aplicación Android nativa desarrollada en un solo archivo con Jetpack Compose y Retrofit, consumiendo API-Football v3 para calcular predicciones y combinadas automáticas.

---

### 1. Obtener API Key gratis en API-Football

1. Entra a [api-football.com](https://www.api-football.com/) y haz clic en **Sign Up / Register**.
2. Completa el registro gratuito (incluye 100 peticiones diarias gratis).
3. Accede al **Dashboard** (Panel de Control) y ve a la sección **Account** o **API Key**.
4. Copia tu clave alfanumérica (`x-apisports-key`).

---

### 2. Cómo subir el proyecto a GitHub desde el celular

1. Abre el navegador de tu celular y entra a [github.com](https://github.com).
2. Inicia sesión y pulsa en el botón **+** (arriba a la derecha) → **New repository**.
3. Nómbralo `FootballStats`, elígelo como **Public** y pulsa **Create repository**.
4. En la pantalla inicial del repositorio, pulsa **uploading an existing file** (o activa "Modo ordenador/escritorio" en las opciones del navegador para ver la interfaz completa).
5. Sube los 7 archivos respetando la estructura de carpetas:
   - `settings.gradle.kts`
   - `build.gradle.kts`
   - `app/build.gradle.kts`
   - `app/src/main/AndroidManifest.xml`
   - `app/src/main/java/com/personal/footballstats/MainActivity.kt`
   - `.github/workflows/build.yml`
   - `README.md`
6. Pulsa **Commit changes** a la rama `main`.

*(Tip: también puedes usar la app de terminal **Termux** o **Git** en Android para clonar y hacer `git push origin main`)*.

---

### 3. Cómo descargar el APK generado desde GitHub Actions

1. Ve a la pestaña **Actions** en tu repositorio de GitHub.
2. Verás ejecutándose el flujo de trabajo llamado **Build APK** (se activa automáticamente al hacer push a `main`).
3. Espera 2-3 minutos hasta que aparezca el icono verde de verificación (éxito).
4. Haz clic sobre la ejecución del workflow.
5. Baja hasta la sección **Artifacts** (al final de la página del resumen).
6. Haz clic en **app-debug-apk** para descargar el archivo `.zip`.
7. Descomprime el archivo en tu celular y pulsa sobre `app-debug.apk` para instalar la aplicación.