# ExposiGuard

Aplicación Android para monitoreo de exposición a radiación electromagnética (EMF).

## Autor

**David L.**  
Email: david.polvo.estelar@gmail.com

## Descripción

ExposiGuard es una aplicación que ayuda a los usuarios a monitorear y entender su exposición diaria a diferentes fuentes de radiación electromagnética, incluyendo:

- Campos electromagnéticos (EMF)
- Redes WiFi
- Señales Bluetooth
- Señales celulares
- Ruido ambiental
- Radiación SAR de dispositivos

## Características

- Monitoreo en tiempo real de señales EMF
- Análisis de tendencias de exposición
- Alertas configurables
- Interfaz intuitiva en español
- Datos de salud integrados (opcional)

## Licencia

Este proyecto está bajo la Licencia MIT - ver el archivo [LICENSE](LICENSE) para más detalles.

## Derechos de Autor

Copyright (c) 2025 David L. Todos los derechos reservados.

La autoría y propiedad intelectual de este código pertenece exclusivamente a David L. El código está disponible bajo licencia MIT para uso personal y no comercial, manteniendo siempre la atribución al autor original.

## Compilación

Para compilar este proyecto necesitarás:

1. **Android Studio** (versión Arctic Fox 2020.3.1 o superior)
2. **SDK de Android** (API 35)
3. **JDK** (versión 17 o superior)

### Opción 1: Usando Android Studio (Recomendado)

1. Abre el proyecto en Android Studio
2. Espera a que se sincronicen las dependencias
3. Ve a `Build > Generate Signed Bundle / APK`
4. Selecciona `APK` y sigue el asistente
5. El APK se generará en `app/build/outputs/apk/release/`

### Opción 2: Usando Gradle (Línea de comandos)

```bash
# Configurar SDK (si no está en la ruta por defecto)
# Edita local.properties con la ruta correcta de tu SDK

# Build debug
./gradlew assembleDebug

# Build release (requiere keystore)
./gradlew assembleRelease
```

### Configuración del SDK

Si el build falla con "SDK location not found":
1. Instala Android Studio si no lo tienes
2. El SDK se instala automáticamente con Android Studio
3. La ruta típica es: `C:\Users\[TuUsuario]\AppData\Local\Android\Sdk`
4. Edita `local.properties` si es necesario

## Contribución

Este proyecto es de código abierto pero no acepta contribuciones externas. Cualquier modificación debe ser realizada por el autor original.