# Plan de desarrollo final — MegaMarket Express

Fecha: 2026-11-04.

Fuente de alcance: ProyectoFinal_App_Antony_De_La_Cruz_Caso05_MegaMarket_Express.html, facilitado por el usuario. El documento se utiliza como especificación del caso académico; sus rutas bancarias, cifras y simuladores no acreditan servicios ni resultados existentes.

## 1. Objetivo y componentes

Ampliar Cliente y Administrador, construir una única App Picker con perfiles PICKER y REPARTIDOR, y ampliar la API existente para cubrir compra, cobro, preparación, entrega y abastecimiento. Incorporar después el modelo analítico, las predicciones y las recomendaciones.

Se mantienen Kotlin, Compose, MVVM, Room, Retrofit y WorkManager. PostgreSQL conserva la autoridad sobre las operaciones compartidas. Las aplicaciones se comunican con MegaMarket; únicamente el servidor se comunica con Banco Andino. No se crea un backend independiente por aplicativo.

La implementación se organiza en entregas funcionales: migración, API, aplicación y pruebas de cada flujo. El soporte offline se diseña en cada etapa, no se agrega al final.

## 2. Decisiones que deben respetarse

- Agregar al carrito reserva; retirar libera; confirmar consume la reserva una sola vez. Preparar, escanear, pagar y entregar no repiten el descuento de la compra.
- La disponibilidad es stock menos reservas. Toda variación del stock contabilizado produce un movimiento con motivo, origen e identificador único.
- Un ajuste por sustitución o cancelación compensa mediante movimientos; no borra el historial ni sobrescribe existencias arbitrariamente. Diferenciar una devolución vendible de una merma física.
- Cada operación conserva propietario y UUID. Solo se envían pendientes de la cuenta activa; cambiar de sesión no reasigna datos.
- La regla de una hora bloquea la creación de nuevos pedidos del cliente. No bloquea el envío posterior de pedidos existentes ni el trabajo offline sobre un picking ya descargado y asignado.
- Separar estado comercial, estado de pago, estado de picking, estado de entrega y estado de sincronización. CONFIRMADO no significa PAGADO ni ENTREGADO.
- Un cobro solo se considera confirmado tras verificación del servidor con el banco. Una captura, un QR mostrado o la declaración del teléfono no acreditan pago.
- El pago contraentrega permite preparar un pedido con pago pendiente. No debe utilizarse una secuencia que obligue a que todos los pedidos estén pagados antes del picking.
- Para sustitutos, el cliente paga como máximo el precio original; si es menor, se registra la devolución correspondiente. El vencimiento de cinco minutos se decide en el servidor con la preferencia guardada al crear la propuesta.
- Mantener los datos académicos y los resultados de demostración identificados como simulados. No presentar el simulador del HTML como modelo entrenado.

## 3. Orden de ejecución

### Etapa 0 — Preparar la base de la siguiente versión

Trabajo:

1. Registrar el estado de las correcciones actuales en el historial del proyecto sin perder cambios locales.
2. Aplicar y verificar sus migraciones en una base PostgreSQL de desarrollo accesible.
3. Ejecutar la prueba de concurrencia pendiente y el recorrido con dos cuentas/dispositivos.
4. Preparar datos reproducibles: usuarios por rol, catálogo, códigos de barras, pasillos y pedidos de prueba.

Salida: una versión de referencia verificable sobre la cual construir. Las correcciones de código se consideran entregadas; su validación de despliegue es una dependencia técnica de la nueva versión.

Situación conocida: la conexión configurada a Neon no resolvió por DNS durante el trabajo anterior. No se debe asumir que la migración remota ya está aplicada.

### Etapa 1 — Diseñar dominio, permisos y contratos

Trabajo:

- Matriz de permisos por recurso, propietario y asignación. El picker trabaja sobre pedidos asignados; el repartidor accede a lo necesario para entregar.
- Estados y transiciones de pedido, pago, ítem, sustitución, entrega y orden de compra; incluir rechazo, vencimiento, cancelación y reintentos.
- Definir el momento de ocupación y liberación de una franja, el rechazo de pedidos offline sin cupo y la asignación exclusiva de picking.
- Contrato OpenAPI de MegaMarket, errores de negocio, paginación, versiones e idempotencia.
- Modelar códigos de barras y presentaciones, ubicación por pasillo, franjas, asignaciones, eventos, pagos y devoluciones.
- Revisar la documentación real del banco: autenticación, QR, consulta de estado, devoluciones, transferencias, crédito, notificaciones y entorno de pruebas.
- Precisar las políticas aún no establecidas: duración de reservas de carrito, cancelación después de pagar, plazo de pago y excepciones de entrega sin ubicación.

Salida: modelo de datos, migraciones propuestas, contratos y diagrama de estados antes de desarrollar pantallas dependientes.

La integración bancaria queda condicionada a las capacidades reales del núcleo docente. Las rutas del HTML son referenciales y no se implementan como si estuvieran verificadas.

### Etapa 2 — Ampliar API operativa y catálogo administrativo

Trabajo:

- Roles PICKER y REPARTIDOR y cuentas de prueba; mantener separación de permisos.
- CRUD de códigos de barras, pasillos y orden de recorrido. Validar unicidad de códigos.
- Franjas de hoy y mañana, con capacidad configurable y valor inicial de 20 pedidos.
- Elección de franja en Cliente y confirmación atómica de su cupo en el servidor.
- Listado/detalle de pedidos, historial de eventos y asignación de picker/repartidor.
- Registrar fecha y responsable de las transiciones para seguimiento y futuros indicadores.

Aceptación: dos solicitudes concurrentes por el último cupo o por una misma asignación no obtienen ambas el recurso. Los estados inválidos y accesos ajenos son rechazados por la API.

Cobertura: RF-C01; base de RF-C06, RF-P01 y RF-A07.

### Etapa 3 — Integrar cobros QR y estado de pago

Trabajo:

- Adaptador bancario exclusivamente en el servidor, con credenciales de entorno y referencias únicas.
- Crear, consultar y verificar cobros; controlar QR vencido, error bancario y respuesta desconocida.
- Recepción de notificaciones verificadas si el banco las ofrece; consulta periódica del servidor como alternativa documentada.
- Evitar pagos duplicados ante reintentos; comprobar comercio, referencia, monto y moneda.
- Pantalla de pago del Cliente y elección de pago en app o contraentrega.
- Un proveedor simulado solo para desarrollo y claramente distinguido del núcleo docente.

Aceptación: ningún teléfono puede marcar PAGADO; repetir la confirmación no genera otro cobro; la prueba real usa el entorno académico del banco.

Cobertura: RF-C02 y base de RF-P05.

### Etapa 4 — Construir App Picker y picking offline

Trabajo:

- Crear módulo Android :picker, con identidad propia e integración en el proyecto existente.
- Login por rol, pedidos asignados y lista ordenada por pasillo con foto, ubicación y cantidad.
- Escáner de códigos de barras con ML Kit; validar producto y unidades, mostrar error y vibración cuando no coincide.
- Evitar escaneos duplicados y cantidades superiores a las solicitadas.
- Room y cola de eventos por usuario; guardar avance offline y sincronizar con UUID y control de versión.
- Tratar reasignaciones, pedidos cancelados y cambios remotos como conflictos visibles.
- Mostrar progreso y estados en Cliente; vista operativa en Administrador.

Aceptación: escaneo correcto e incorrecto, reinicio sin pérdida de avance y reconexión sin duplicados. Preparar no vuelve a descontar stock. Medir la respuesta de escaneo en dispositivo; objetivo del caso: menos de un segundo.

Cobertura: RF-P01, RF-P02, RF-P03 y RF-C06.

### Etapa 5 — Faltantes, sustitutos y notificaciones

Trabajo:

- Marcar faltantes y sugerir productos de la misma categoría, disponibles y dentro de ±20 % del precio original.
- Primera versión determinista y explicable; añadir ranking aprendido después.
- Propuesta con UUID, fecha límite y preferencia del cliente capturada en el servidor.
- Notificar, aprobar/rechazar y consultar respuesta. Recuperar propuestas desde la API aunque falle o se deniegue la notificación.
- Resolver la carrera entre respuesta y vencimiento de cinco minutos con una sola decisión final.
- Reservar el sustituto y ajustar movimientos, detalle e importes sin cobrar de más.
- Registrar y verificar devoluciones bancarias; separar decisión logística de devolución financiera pendiente.

Aceptación: precio superior mantiene el precio original; precio inferior devuelve la diferencia; decisión repetida o fuera de plazo no aplica dos veces.

Cobertura: RF-C04, RF-C05 y RF-P04.

### Etapa 6 — Reparto, cobro contraentrega y evidencia

Trabajo:

- Flujo del REPARTIDOR dentro de App Picker: pedidos listos asignados, salida y entrega.
- Reutilizar cobros QR, incluida la confirmación bancaria del pago contraentrega.
- Capturar foto, fecha y ubicación; gestionar permisos y fallos de captura/subida.
- Guardar archivos localmente y subirlos con reintentos e identificación estable.
- Diferenciar evidencia capturada offline de entrega definitivamente cerrada en el servidor.
- Proteger evidencias y direcciones mediante autorización; no usar archivos públicos por defecto.

Aceptación: evidencia y pago se sincronizan sin duplicarse; no se cierra una entrega que incumple sus requisitos. El cliente distingue entrega pendiente de validación y entrega finalizada.

Cobertura: RF-P05 y RF-P06.

### Etapa 7 — Proveedores y órdenes de compra

Trabajo:

- Proveedores, cuentas bancarias y relación con productos/plazos de suministro.
- Orden de compra con detalles, cantidades, costos y estados SUGERIDA, APROBADA, PAGADA y RECIBIDA; contemplar pagos fallidos o pendientes.
- Aprobación del administrador, transferencia mediante el adaptador y consulta de confirmación.
- Recepción con movimientos de entrada idempotentes, incluido control de recepción parcial.
- Pagar no incrementa stock; únicamente recibir mercadería lo incrementa.

Aceptación: una orden no se paga sin aprobación; un reintento no duplica transferencia ni recepción.

Cobertura: RF-A03, RF-A04 y estructura de RF-A02.

### Etapa 8 — Conciliación y crédito

Trabajo:

- Libro de operaciones de cobro, devolución y pago a proveedores, con referencias bancarias.
- Conciliación diaria por fecha de negocio de la tienda, diferencias justificadas y cierre auditado.
- Resumen agregado de ventas con vista previa y autorización explícita antes de compartirlo.
- Solicitud y consulta de crédito; registrar estados informados por el banco. La aprobación corresponde al comité externo.

Aceptación: no se cierra el día con diferencias sin justificar; el resumen enviado no incluye datos personales de clientes; no se simula una aprobación bancaria como respuesta real.

Cobertura: RF-A05 y RF-A06.

### Etapa 9 — Datos analíticos, tablero y Power BI

Trabajo:

- ETL repetible desde pedidos, eventos, pagos, compras y movimientos hacia un modelo estrella separado del esquema operativo.
- Hechos de ventas, stock y compras; dimensiones de fecha, producto, franja, medio de pago, proveedor y picker cuando correspondan.
- Incluir ventas netas, devoluciones y sustituciones sin contar reintentos como nuevas ventas.
- Indicadores: ventas, ticket, duración de picking, sustituciones, stock y pagos digitales; comparación semanal.
- Tablero Android y tablero Power BI, con fecha de actualización y origen de datos.

Aceptación: cada KPI cuadra con su fuente y define fórmula, período y exclusiones. Las cifras del HTML son ejemplos, no datos para fijar en la interfaz.

Cobertura: RF-A07 y entregable BI.

### Etapa 10 — Predicciones y recomendaciones

Orden interno:

1. ML-01: demanda y riesgo de quiebre; promedio móvil como referencia, comparación con Gradient Boosting y evaluación temporal. Usar MAPE solo donde la demanda no sea cero y complementar con MAE. Distinguir venta observada de demanda no satisfecha.
2. Aplicar el pronóstico a alertas y órdenes sugeridas: considerar stock disponible, entradas pendientes, plazo del proveedor y stock de seguridad. La compra sigue requiriendo aprobación humana.
3. ML-02: ranking de sustitutos por atributos y aceptación histórica, manteniendo los filtros comerciales obligatorios.
4. ML-03: Apriori para sugerencias de canasta, hasta tres recomendaciones con motivo, confianza y lift.
5. ML-04: estimación de duración del picking y MAE en minutos, con una regla simple de respaldo si faltan datos.

Recolectar eventos desde la etapa 2. Para demostración se pueden generar datos históricos sintéticos reproducibles y etiquetados; no se mezclan con resultados observados sin distinguir su origen.

Aceptación: notebooks reproducibles, partición de entrenamiento/evaluación, métricas calculadas y servicio de inferencia versionado. No afirmar quiebres evitados sin una comparación medible.

Cobertura: RF-A01, RF-A02, RF-C07 y los cuatro casos ML del documento.

### Etapa 11 — Integración y sustentación

- Recorrer compra → pago o contraentrega → picking → sustitución → entrega → conciliación.
- Recorrer riesgo de quiebre → sugerencia → aprobación → pago a proveedor → recepción.
- Probar las tres apps sin señal y reconectarlas, con cambio de usuario y reenvíos.
- Verificar migraciones sobre copias de datos, permisos por rol, respaldo de evidencias y fallos del banco.
- Preparar catálogo de al menos 30 productos, cuentas ficticias, guion de demostración, notebook y Power BI.
- Documentar instalación, configuración, contratos, decisiones y un cambio pequeño ejecutable en vivo.

## 4. Prioridades y entregas

Los requisitos M del documento son obligatorios; S son posteriores a sus dependencias y C son complementarios. Ninguno se considera implementado por existir una pantalla o un simulador.

| Entrega | Etapas | Resultado demostrable |
|---|---|---|
| Base y diseño | 0–1 | Datos, permisos, estados y contratos acordados |
| Compra programada y pago | 2–3 | Pedido con cupo y pago verificado o contraentrega |
| Preparación | 4–5 | App Picker, escaneo offline y sustitución aceptada |
| Entrega | 6 | Reparto, evidencia y cierre verificado |
| Abastecimiento y caja | 7–8 | Proveedor pagado, recepción, conciliación y crédito |
| Inteligencia | 9–10 | Tablero, Power BI y modelos evaluados |
| Evaluación final | 11 | Tres apps y servicios integrados, probados y documentados |

Las semanas 11–16 del HTML son hitos académicos de referencia, no fechas de calendario confirmadas. La estimación se hará por entrega cuando se conozcan la fecha límite y los contratos del banco.

## 5. Criterio para cerrar cada etapa

Una etapa termina cuando su migración está probada, el contrato está documentado, los permisos se verifican en servidor, el flujo funciona entre API y aplicaciones afectadas, los reintentos no duplican operaciones y existen pruebas de sus fallos relevantes. Las pantallas incluyen carga, vacío, error y conflicto cuando correspondan.

Antes de desplegar, actualizar de forma coordinada los componentes que compartan cambios incompatibles. No acumular toda la verificación para la evaluación final.

## 6. Primer trabajo de implementación

Comenzar por etapas 0 y 1: validar la base desplegable, levantar la matriz de requisitos/roles, definir estados y documentar contratos. Después implementar la ampliación de catálogo, franjas y asignación de la etapa 2. El módulo :picker se construye sobre esos contratos, y la integración bancaria se verifica desde el inicio para detectar dependencias externas a tiempo.
