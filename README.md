# MegaMarket Express

Proyecto Android con dos aplicativos y una API REST.

| Módulo | Función |
|---|---|
| app | Administración: catálogo, productos, precios, stock, ofertas y sincronización |
| cliente | Catálogo, favoritos, carrito con reservas, checkout y seguimiento de pedidos |
| modelo | Modelos y reglas compartidas |
| api-rest | Express, autenticación JWT y PostgreSQL |

## Reglas actuales

- Room alimenta la interfaz; Retrofit conecta con la API y WorkManager procesa pendientes.
- Agregar al carrito reserva unidades; reducir o quitar libera la reserva.
- La compra consume la reserva: no descuenta dos veces el disponible.
- Sin conexión, las reservas son provisionales; el servidor confirma stock y precios al sincronizar.
- Cada carrito, favorito y operación pertenece a una cuenta. Solo se envían pendientes de la cuenta activa.
- Si catálogo y stock llevan más de una hora sin actualizarse correctamente, se bloquean nuevos pedidos.
- Reiniciar el dispositivo obliga a actualizar antes de comprar. Cambiar el reloj no extiende el plazo.
- Los cambios de precio requieren aceptación explícita. Los pedidos se consultan desde Perfil.
- Los productos con nombres equivalentes no se duplican; se ofrece editar el existente.
- El administrador recibe productos activos e inactivos; el cliente solo puede comprar activos.
- Las reservas no vencen automáticamente. Cerrar sesión no elimina pendientes ni reservas.

## Preparación

1. Configurar Android SDK y JDK compatibles con el proyecto y abrir la raíz en Android Studio.
2. Configurar MEGAMARKET_API_BASE_URL en local.properties. El valor predeterminado es http://10.0.2.2:8010/.
3. Preparar PostgreSQL siguiendo api-rest/README.md. Las instalaciones existentes requieren sql/002_reservas.sql.
4. Configurar DATABASE_URL, JWT_SECRET y PORT en api-rest/.env, sin versionar credenciales.
5. Instalar dependencias y arrancar la API. Usar PORT=8010 para coincidir con el valor Android predeterminado.
6. Instalar las nuevas versiones de ambos aplicativos. El primer acceso de cada cuenta requiere internet.

Las claves locales HTTP permitidas están definidas en los archivos network_security_config.xml; un teléfono físico necesita una URL alcanzable y una configuración de red apropiada.

## Verificación

Desde la raíz:

~~~powershell
.\gradlew.bat :app:testDebugUnitTest :cliente:testDebugUnitTest :modelo:testDebugUnitTest :app:assembleDebug :cliente:assembleDebug
~~~

Desde api-rest, con Node.js 24 o posterior:

~~~powershell
npm ci
npm test
npm start
~~~

Las pruebas del backend ejecutan controladores sobre SQLite en memoria mediante un adaptador; no sustituyen pruebas de bloqueos concurrentes en PostgreSQL.

Ver detalles de migración, compatibilidad y pruebas manuales en docs/IMPLEMENTACION_RESERVAS.md. Los requisitos iniciales se conservan en docs/REQUISITOS_ORIGINALES.md como referencia histórica.
