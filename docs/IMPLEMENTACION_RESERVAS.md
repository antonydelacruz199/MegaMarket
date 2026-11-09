# Reservas, cuentas y confirmación de pedidos

## Modelo de existencias

PostgreSQL conserva el stock físico en productos.stock. La disponibilidad se calcula restando las reservas activas. Agregar o quitar del carrito modifica reservas; confirmar reduce el físico y consume las reservas dentro de la misma transacción. El disponible no se descuenta dos veces.

PUT /api/carritos/:id recibe una instantánea completa con revision e items. La revisión crece por carrito y dispositivo. Una revisión antigua no reemplaza una nueva. Los carritos cerrados no se reabren por solicitudes demoradas. Cada dispositivo usa UUID distintos y cada carrito conserva su dueño.

El cliente almacena el disponible y descuenta inmediatamente sus cambios locales. Al descargar, concilia las reservas propias reconocidas por el servidor con las reservas locales de esa cuenta. Las reservas de otras cuentas se reflejan exclusivamente mediante la disponibilidad publicada por el servidor: no se exponen sus identificadores.

Sin red, no es posible reservar globalmente. Una reserva provisional puede ser rechazada si otro dispositivo utilizó el stock. La confirmación del servidor siempre es necesaria.

## Pedidos

POST /api/pedidos requiere contratoVersion=2, clientUuid, dirección e ítems con precioEsperadoCentimos. El UUID corresponde al carrito del que procede la compra.

El servidor agrupa productos repetidos, bloquea productos en un orden estable, descuenta otras reservas, valida precio y registra pedido, dirección, detalles y movimiento de venta en una transacción. Los reintentos verifican propietario y huella del contenido; no devuelven pedidos ajenos ni aceptan cambios bajo el mismo identificador confirmado.

Estados locales:

- PENDIENTE_CONFIRMACION: registrado localmente, aún sin confirmación definitiva.
- REQUIERE_REVISION: el servidor rechazó la solicitud; el usuario puede revisar el total y reintentar o cancelar para liberar la reserva.
- CONFIRMADO: aceptado por el servidor.
- CANCELADO: cancelado localmente después de un rechazo; la liberación remota queda en cola si no hay conexión.

Los pedidos con respuesta ambigua por fallo de red permanecen pendientes y se reintentan con el mismo UUID. No se permite cancelarlos como si se supiera que el servidor los rechazó.

En Perfil se muestran los pedidos de la cuenta activa. Desde el detalle se actualiza el estado, se acepta el importe actualizado o se cancela un pedido que requiere revisión.

## Identidad y sesiones

Room guarda el dueño de cada operación. La cola se procesa por cuenta y fecha. Los pedidos de una cuenta sin sesión esperan a su propietario. Cada solicitud de escritura fija la identidad esperada y obtiene el token de esa cuenta; un cambio de cuenta antes del envío la detiene. Una solicitud que ya llegó al servidor conserva la identidad de su token original.

Si vence el JWT, el usuario debe autenticarse nuevamente con conexión. El acceso local no renueva tokens del servidor.

## Vigencia de datos

Solo una descarga completa y guardada acredita frescura. Un envío de pedidos, un fallo de descarga o un intento de sincronización no renuevan el plazo. A los 60 minutos exactos sigue permitido crear pedidos; después se bloquea. Se verifican reloj de pared, tiempo monotónico y contador de arranque. Tras reiniciar se exige actualizar.

## Migración del servidor

1. Revisar el esquema real y realizar una copia de seguridad.
2. Buscar nombres duplicados antes de aplicar el índice:

```sql
SELECT lower(regexp_replace(trim(nombre), '\s+', ' ', 'g')) nombre_normalizado,
       array_agg(id) ids, count(*) cantidad
FROM productos WHERE deleted_at IS NULL
GROUP BY 1 HAVING count(*) > 1;
```

3. Resolver esas coincidencias manualmente. No se fusionan productos automáticamente.
4. En una base existente ejecutar api-rest/sql/002_reservas.sql. En una base nueva ejecutar primero 001_esquema_inicial.sql y después 002_reservas.sql.
5. Desplegar API y aplicativos actualizados de forma coordinada. El endpoint de pedidos rechaza clientes antiguos con HTTP 426.
6. Mantener las escrituras detenidas durante la migración. No revertir la API mientras haya reservas activas: el protocolo anterior no las respeta.

Los scripts presuponen identificadores UUID y las tablas utilizadas por los controladores originales. No se ha aplicado la migración a Neon durante esta implementación: la conexión configurada no resolvió por DNS.

## Migración Android

Cliente pasa de Room 7 a 8. Conserva pedidos y recupera el propietario de sus operaciones a partir de la relación con clientes. Los carritos y favoritos antiguos sin propietario quedan conservados con ownerId=0 y no se asignan a la siguiente cuenta que ingrese. Las operaciones sin propietario identificable también quedan aisladas. La primera actualización de cada cuenta requiere login remoto para vincular su perfil local con su UUID del servidor.

## Verificación manual de integración

Usar una base de pruebas PostgreSQL, dos cuentas cliente y dos dispositivos:

1. Reservar las últimas unidades simultáneamente: no deben reservarse más que las existentes.
2. Aumentar, reducir y quitar: la disponibilidad debe cambiar en ambos dispositivos después de sincronizar.
3. Confirmar y perder la respuesta; reintentar: un solo pedido y un solo descuento.
4. Crear un pedido offline con Ana, ingresar con Pedro y recuperar red: el pedido de Ana espera; al volver Ana se procesa con su identidad.
5. Cambiar precio antes de enviar: el pedido requiere revisión; aceptar el total actualizado confirma sin duplicar reserva.
6. Agotar stock desde otro dispositivo antes de enviar una reserva offline: informar rechazo, permitir revisar/cancelar y liberar.
7. Desactivar un producto reservado: impedir su compra y permitir retirarlo del carrito.
8. Superar una hora, cambiar reloj y reiniciar: bloquear nuevos pedidos hasta actualizar correctamente.
9. Crear nombres equivalentes desde dos administradores: una creación, un conflicto y opción de editar el existente.

Las pruebas automatizadas locales no verifican bloqueos ni triggers PostgreSQL. Esta comprobación de integración permanece necesaria antes de desplegar.
