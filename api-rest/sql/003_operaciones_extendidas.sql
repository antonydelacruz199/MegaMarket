-- MegaMarket · migración 003 — operaciones extendidas (alineada a Neon)
-- Orden: 001_esquema_inicial.sql → 002_reservas.sql → este archivo.
-- Idempotente frente a un Neon que ya tenga estas estructuras.
-- Conserva filas existentes (incl. pedidos estado CONFIRMADO).
-- No elimina tablas/columnas/registros. No se ejecuta al arrancar la API.
--
-- Fuente: introspección de solo lectura del esquema public en Neon (2026-11-11).
-- No incluye personal_access_tokens (ajeno al dominio MegaMarket Node/JWT).

BEGIN;

CREATE OR REPLACE FUNCTION megamarket_agregar_check(
  p_tabla regclass,
  p_nombre text,
  p_expresion text
) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = p_nombre
  ) THEN
    EXECUTE format(
      'ALTER TABLE %s ADD CONSTRAINT %I CHECK (%s)',
      p_tabla, p_nombre, p_expresion
    );
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION megamarket_reemplazar_check(
  p_tabla regclass,
  p_nombres text[],
  p_nuevo_nombre text,
  p_expresion text
) RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
  n text;
BEGIN
  FOREACH n IN ARRAY p_nombres LOOP
    EXECUTE format('ALTER TABLE %s DROP CONSTRAINT IF EXISTS %I', p_tabla, n);
  END LOOP;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = p_nuevo_nombre
  ) THEN
    EXECUTE format(
      'ALTER TABLE %s ADD CONSTRAINT %I CHECK (%s)',
      p_tabla, p_nuevo_nombre, p_expresion
    );
  END IF;
END;
$$;

CREATE OR REPLACE FUNCTION megamarket_agregar_fk(
  p_tabla regclass,
  p_nombre text,
  p_definicion text
) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = p_nombre
  ) THEN
    EXECUTE format(
      'ALTER TABLE %s ADD CONSTRAINT %I %s',
      p_tabla, p_nombre, p_definicion
    );
  END IF;
END;
$$;

-- ---------------------------------------------------------------------------
-- usuarios: roles operativos + preferencia de sustitución
-- ---------------------------------------------------------------------------
SELECT megamarket_reemplazar_check(
  'usuarios'::regclass,
  ARRAY['ck_usuarios_rol', 'usuarios_rol_check'],
  'ck_usuarios_rol',
  $$rol IN ('CLIENTE','ADMINISTRADOR','PICKER','REPARTIDOR')$$
);

ALTER TABLE usuarios
  ADD COLUMN IF NOT EXISTS preferencia_sustitucion varchar NOT NULL DEFAULT 'RECHAZAR';

SELECT megamarket_agregar_check(
  'usuarios'::regclass,
  'ck_usuarios_preferencia_sustitucion',
  $$preferencia_sustitucion IN ('ACEPTAR','RECHAZAR')$$
);

-- ---------------------------------------------------------------------------
-- productos: código de barras y pasillo (texto; sin tablas auxiliares)
-- ---------------------------------------------------------------------------
ALTER TABLE productos ADD COLUMN IF NOT EXISTS codigo_barras varchar;
ALTER TABLE productos ADD COLUMN IF NOT EXISTS pasillo varchar;

CREATE UNIQUE INDEX IF NOT EXISTS uq_productos_codigo_barras
  ON productos (codigo_barras)
  WHERE codigo_barras IS NOT NULL AND deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- franjas_entrega
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS franjas_entrega (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  fecha date NOT NULL,
  hora_inicio time NOT NULL,
  hora_fin time NOT NULL,
  capacidad integer NOT NULL DEFAULT 20,
  activo boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_franjas_capacidad CHECK (capacidad >= 1 AND capacidad <= 20),
  CONSTRAINT ck_franjas_horas CHECK (hora_fin > hora_inicio),
  CONSTRAINT uq_franjas_fecha_horario UNIQUE (fecha, hora_inicio, hora_fin)
);

-- ---------------------------------------------------------------------------
-- pedidos: franja, asignaciones, preferencia; estados ampliados (CONFIRMADO intacto)
-- ---------------------------------------------------------------------------
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS franja_entrega_id uuid;
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS picker_id uuid;
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS repartidor_id uuid;
ALTER TABLE pedidos
  ADD COLUMN IF NOT EXISTS preferencia_sustitucion varchar NOT NULL DEFAULT 'RECHAZAR';

SELECT megamarket_agregar_fk(
  'pedidos'::regclass,
  'pedidos_franja_entrega_id_fkey',
  'FOREIGN KEY (franja_entrega_id) REFERENCES franjas_entrega(id)'
);
SELECT megamarket_agregar_fk(
  'pedidos'::regclass,
  'pedidos_picker_id_fkey',
  'FOREIGN KEY (picker_id) REFERENCES usuarios(id)'
);
SELECT megamarket_agregar_fk(
  'pedidos'::regclass,
  'pedidos_repartidor_id_fkey',
  'FOREIGN KEY (repartidor_id) REFERENCES usuarios(id)'
);

SELECT megamarket_agregar_check(
  'pedidos'::regclass,
  'ck_pedidos_preferencia_sustitucion',
  $$preferencia_sustitucion IN ('ACEPTAR','RECHAZAR')$$
);

-- Sustituye el check laxo de instalaciones previas; incluye CONFIRMADO histórico.
SELECT megamarket_reemplazar_check(
  'pedidos'::regclass,
  ARRAY['ck_pedidos_estado', 'pedidos_estado_check'],
  'ck_pedidos_estado',
  $$estado IN (
    'CONFIRMADO','PAGADO','EN_PICKING','LISTO','EN_CAMINO','ENTREGADO','CANCELADO'
  )$$
);

CREATE INDEX IF NOT EXISTS idx_pedidos_franja ON pedidos (franja_entrega_id);
CREATE INDEX IF NOT EXISTS idx_pedidos_picker ON pedidos (picker_id);
CREATE INDEX IF NOT EXISTS idx_pedidos_repartidor ON pedidos (repartidor_id);

-- ---------------------------------------------------------------------------
-- pedido_detalle: avance de picking
-- ---------------------------------------------------------------------------
ALTER TABLE pedido_detalle
  ADD COLUMN IF NOT EXISTS estado_picking varchar NOT NULL DEFAULT 'PENDIENTE';
ALTER TABLE pedido_detalle
  ADD COLUMN IF NOT EXISTS cantidad_armada integer NOT NULL DEFAULT 0;
ALTER TABLE pedido_detalle ADD COLUMN IF NOT EXISTS fecha_escaneo timestamptz;
ALTER TABLE pedido_detalle ADD COLUMN IF NOT EXISTS escaneado_por uuid;

SELECT megamarket_agregar_fk(
  'pedido_detalle'::regclass,
  'pedido_detalle_escaneado_por_fkey',
  'FOREIGN KEY (escaneado_por) REFERENCES usuarios(id)'
);

SELECT megamarket_agregar_check(
  'pedido_detalle'::regclass,
  'ck_pedido_detalle_estado_picking',
  $$estado_picking IN ('PENDIENTE','ARMADO','FALTANTE','SUSTITUIDO','ELIMINADO')$$
);
SELECT megamarket_agregar_check(
  'pedido_detalle'::regclass,
  'ck_pedido_detalle_cantidad_armada',
  $$cantidad_armada >= 0 AND cantidad_armada <= cantidad$$
);

-- ---------------------------------------------------------------------------
-- movimientos_inventario: recepción de compra
-- ---------------------------------------------------------------------------
ALTER TABLE movimientos_inventario
  ADD COLUMN IF NOT EXISTS orden_compra_detalle_id uuid;

SELECT megamarket_reemplazar_check(
  'movimientos_inventario'::regclass,
  ARRAY['chk_movimiento_tipo', 'movimientos_inventario_tipo_check'],
  'chk_movimiento_tipo',
  $$tipo IN ('SALIDA_VENTA','AJUSTE_ENTRADA','AJUSTE_SALIDA','ENTRADA_COMPRA')$$
);

SELECT megamarket_agregar_check(
  'movimientos_inventario'::regclass,
  'ck_movimiento_entrada_compra',
  $$(tipo <> 'ENTRADA_COMPRA' OR orden_compra_detalle_id IS NOT NULL)$$
);

-- FK a orden_compra_detalle se agrega después de crear esa tabla.

CREATE INDEX IF NOT EXISTS idx_movimientos_orden_compra_detalle
  ON movimientos_inventario (orden_compra_detalle_id);

-- ---------------------------------------------------------------------------
-- pagos
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pagos (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  uuid_operacion uuid NOT NULL,
  pedido_id uuid NOT NULL REFERENCES pedidos(id),
  monto_centimos bigint NOT NULL,
  moneda varchar NOT NULL DEFAULT 'PEN',
  medio_pago varchar NOT NULL DEFAULT 'QR',
  modalidad varchar NOT NULL DEFAULT 'ONLINE',
  estado varchar NOT NULL DEFAULT 'PENDIENTE',
  referencia_banco varchar,
  numero_operacion_banco varchar,
  qr_contenido text,
  fecha_expiracion_qr timestamptz,
  fecha_confirmacion timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT pagos_uuid_operacion_key UNIQUE (uuid_operacion),
  CONSTRAINT ck_pagos_monto CHECK (monto_centimos > 0),
  CONSTRAINT ck_pagos_moneda CHECK (moneda = 'PEN'),
  CONSTRAINT ck_pagos_medio CHECK (medio_pago = 'QR'),
  CONSTRAINT ck_pagos_modalidad CHECK (modalidad IN ('ONLINE','CONTRAENTREGA')),
  CONSTRAINT ck_pagos_estado CHECK (estado IN (
    'PENDIENTE','QR_GENERADO','CONFIRMADO','RECHAZADO','EXPIRADO','CANCELADO'
  )),
  CONSTRAINT ck_pagos_confirmacion CHECK (
    estado <> 'CONFIRMADO'
    OR (numero_operacion_banco IS NOT NULL AND fecha_confirmacion IS NOT NULL)
  )
);

CREATE INDEX IF NOT EXISTS idx_pagos_pedido ON pagos (pedido_id);
CREATE INDEX IF NOT EXISTS idx_pagos_estado ON pagos (estado);
CREATE UNIQUE INDEX IF NOT EXISTS uq_pagos_confirmado_pedido
  ON pagos (pedido_id) WHERE estado = 'CONFIRMADO';
CREATE UNIQUE INDEX IF NOT EXISTS uq_pagos_operacion_banco
  ON pagos (numero_operacion_banco) WHERE numero_operacion_banco IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_pagos_referencia_banco
  ON pagos (referencia_banco) WHERE referencia_banco IS NOT NULL;

-- ---------------------------------------------------------------------------
-- sustituciones_pedido (antes que ajustes_pago por FK)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sustituciones_pedido (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  pedido_detalle_id uuid NOT NULL REFERENCES pedido_detalle(id),
  producto_original_id uuid NOT NULL REFERENCES productos(id),
  producto_sustituto_id uuid NOT NULL REFERENCES productos(id),
  picker_id uuid NOT NULL REFERENCES usuarios(id),
  cantidad integer NOT NULL,
  precio_original_centimos bigint NOT NULL,
  precio_sustituto_centimos bigint NOT NULL,
  estado varchar NOT NULL DEFAULT 'PENDIENTE',
  fecha_propuesta timestamptz NOT NULL DEFAULT now(),
  fecha_limite timestamptz NOT NULL DEFAULT (now() + interval '5 minutes'),
  fecha_respuesta timestamptz,
  respuesta_automatica boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_sustituciones_cantidad CHECK (cantidad > 0),
  CONSTRAINT ck_sustituciones_precios CHECK (
    precio_original_centimos > 0 AND precio_sustituto_centimos > 0
  ),
  CONSTRAINT ck_sustituciones_productos CHECK (
    producto_original_id <> producto_sustituto_id
  ),
  CONSTRAINT ck_sustituciones_estado CHECK (estado IN (
    'PENDIENTE','ACEPTADA','RECHAZADA','CANCELADA'
  ))
);

CREATE INDEX IF NOT EXISTS idx_sustituciones_detalle
  ON sustituciones_pedido (pedido_detalle_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_sustituciones_pendiente
  ON sustituciones_pedido (pedido_detalle_id) WHERE estado = 'PENDIENTE';

-- ---------------------------------------------------------------------------
-- ajustes_pago
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ajustes_pago (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  uuid_operacion uuid NOT NULL,
  pago_id uuid NOT NULL REFERENCES pagos(id),
  sustitucion_id uuid REFERENCES sustituciones_pedido(id),
  tipo varchar NOT NULL,
  motivo varchar NOT NULL,
  monto_centimos bigint NOT NULL,
  estado varchar NOT NULL DEFAULT 'PENDIENTE',
  referencia_banco varchar,
  numero_operacion_banco varchar,
  fecha_confirmacion timestamptz,
  observaciones text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ajustes_pago_uuid_operacion_key UNIQUE (uuid_operacion),
  CONSTRAINT ajustes_pago_referencia_banco_key UNIQUE (referencia_banco),
  CONSTRAINT ajustes_pago_numero_operacion_banco_key UNIQUE (numero_operacion_banco),
  CONSTRAINT ck_ajustes_pago_tipo CHECK (tipo IN ('DESCUENTO','DEVOLUCION')),
  CONSTRAINT ck_ajustes_pago_motivo CHECK (motivo IN (
    'SUSTITUCION','PRODUCTO_FALTANTE','OTRO'
  )),
  CONSTRAINT ck_ajustes_pago_monto CHECK (monto_centimos > 0),
  CONSTRAINT ck_ajustes_pago_estado CHECK (estado IN (
    'PENDIENTE','CONFIRMADO','RECHAZADO','CANCELADO'
  )),
  CONSTRAINT ck_ajustes_pago_confirmacion CHECK (
    estado <> 'CONFIRMADO' OR fecha_confirmacion IS NOT NULL
  ),
  CONSTRAINT ck_ajustes_pago_sustitucion CHECK (
    motivo <> 'SUSTITUCION' OR sustitucion_id IS NOT NULL
  )
);

CREATE INDEX IF NOT EXISTS idx_ajustes_pago_estado ON ajustes_pago (estado);
CREATE INDEX IF NOT EXISTS idx_ajustes_pago_pago ON ajustes_pago (pago_id);
CREATE INDEX IF NOT EXISTS idx_ajustes_pago_sustitucion ON ajustes_pago (sustitucion_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ajustes_pago_sustitucion_tipo
  ON ajustes_pago (pago_id, sustitucion_id, tipo)
  WHERE sustitucion_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- picking_escaneos
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS picking_escaneos (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  uuid_operacion uuid NOT NULL,
  pedido_detalle_id uuid NOT NULL REFERENCES pedido_detalle(id),
  picker_id uuid NOT NULL REFERENCES usuarios(id),
  codigo_barras varchar NOT NULL,
  cantidad integer NOT NULL DEFAULT 1,
  resultado varchar NOT NULL,
  fecha_escaneo timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT picking_escaneos_uuid_operacion_key UNIQUE (uuid_operacion),
  CONSTRAINT ck_picking_escaneos_cantidad CHECK (cantidad > 0),
  CONSTRAINT ck_picking_escaneos_resultado CHECK (resultado IN (
    'CORRECTO','INCORRECTO'
  ))
);

CREATE INDEX IF NOT EXISTS idx_picking_escaneos_detalle
  ON picking_escaneos (pedido_detalle_id);
CREATE INDEX IF NOT EXISTS idx_picking_escaneos_picker
  ON picking_escaneos (picker_id);

-- ---------------------------------------------------------------------------
-- proveedores / órdenes de compra
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS proveedores (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  ruc varchar NOT NULL,
  razon_social varchar NOT NULL,
  telefono varchar,
  correo varchar,
  cuenta_bancaria varchar,
  activo boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT proveedores_ruc_key UNIQUE (ruc),
  CONSTRAINT ck_proveedores_ruc CHECK (ruc ~ '^[0-9]{11}$'),
  CONSTRAINT ck_proveedores_razon_social CHECK (TRIM(BOTH FROM razon_social) <> '')
);

CREATE TABLE IF NOT EXISTS ordenes_compra (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  proveedor_id uuid NOT NULL REFERENCES proveedores(id),
  administrador_id uuid REFERENCES usuarios(id),
  numero_orden varchar NOT NULL,
  estado varchar NOT NULL DEFAULT 'SUGERIDA',
  total_centimos bigint NOT NULL DEFAULT 0,
  numero_operacion_banco varchar,
  fecha_aprobacion timestamptz,
  fecha_pago timestamptz,
  fecha_recepcion timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ordenes_compra_numero_orden_key UNIQUE (numero_orden),
  CONSTRAINT ck_ordenes_compra_estado CHECK (estado IN (
    'SUGERIDA','APROBADA','PAGADA','RECIBIDA','CANCELADA'
  )),
  CONSTRAINT ck_ordenes_compra_total CHECK (total_centimos >= 0)
);

CREATE INDEX IF NOT EXISTS idx_ordenes_compra_estado ON ordenes_compra (estado);
CREATE INDEX IF NOT EXISTS idx_ordenes_compra_proveedor ON ordenes_compra (proveedor_id);

CREATE TABLE IF NOT EXISTS orden_compra_detalle (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  orden_compra_id uuid NOT NULL REFERENCES ordenes_compra(id),
  producto_id uuid NOT NULL REFERENCES productos(id),
  cantidad integer NOT NULL,
  precio_unitario_centimos bigint NOT NULL,
  subtotal_centimos bigint NOT NULL,
  cantidad_recibida integer NOT NULL DEFAULT 0,
  CONSTRAINT uq_orden_compra_producto UNIQUE (orden_compra_id, producto_id),
  CONSTRAINT ck_orden_compra_cantidad CHECK (cantidad > 0),
  CONSTRAINT ck_orden_compra_precio CHECK (precio_unitario_centimos > 0),
  CONSTRAINT ck_orden_compra_subtotal CHECK (
    subtotal_centimos = (cantidad * precio_unitario_centimos)
  ),
  CONSTRAINT ck_orden_compra_recepcion CHECK (
    cantidad_recibida >= 0 AND cantidad_recibida <= cantidad
  )
);

CREATE INDEX IF NOT EXISTS idx_orden_compra_detalle_producto
  ON orden_compra_detalle (producto_id);

SELECT megamarket_agregar_fk(
  'movimientos_inventario'::regclass,
  'fk_movimiento_orden_compra_detalle',
  'FOREIGN KEY (orden_compra_detalle_id) REFERENCES orden_compra_detalle(id)'
);

-- ---------------------------------------------------------------------------
-- entregas
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS entregas (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  uuid_operacion uuid NOT NULL,
  pedido_id uuid NOT NULL REFERENCES pedidos(id),
  repartidor_id uuid NOT NULL REFERENCES usuarios(id),
  foto_referencia text NOT NULL,
  latitud numeric NOT NULL,
  longitud numeric NOT NULL,
  fecha_entrega timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT entregas_uuid_operacion_key UNIQUE (uuid_operacion),
  CONSTRAINT uq_entregas_pedido UNIQUE (pedido_id),
  CONSTRAINT ck_entregas_foto CHECK (TRIM(BOTH FROM foto_referencia) <> ''),
  CONSTRAINT ck_entregas_latitud CHECK (latitud >= -90 AND latitud <= 90),
  CONSTRAINT ck_entregas_longitud CHECK (longitud >= -180 AND longitud <= 180)
);

CREATE INDEX IF NOT EXISTS idx_entregas_fecha ON entregas (fecha_entrega);
CREATE INDEX IF NOT EXISTS idx_entregas_repartidor ON entregas (repartidor_id);

-- ---------------------------------------------------------------------------
-- conciliaciones
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conciliaciones_bancarias (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  fecha date NOT NULL,
  estado varchar NOT NULL DEFAULT 'ABIERTA',
  administrador_id uuid NOT NULL REFERENCES usuarios(id),
  fecha_cierre timestamptz,
  observaciones text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT conciliaciones_bancarias_fecha_key UNIQUE (fecha),
  CONSTRAINT ck_conciliacion_estado CHECK (estado IN ('ABIERTA','CERRADA')),
  CONSTRAINT ck_conciliacion_cierre CHECK (
    (estado = 'ABIERTA' AND fecha_cierre IS NULL)
    OR (estado = 'CERRADA' AND fecha_cierre IS NOT NULL)
  )
);

CREATE TABLE IF NOT EXISTS conciliacion_detalle (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conciliacion_id uuid NOT NULL REFERENCES conciliaciones_bancarias(id),
  tipo_operacion varchar NOT NULL,
  pago_id uuid REFERENCES pagos(id),
  orden_compra_id uuid REFERENCES ordenes_compra(id),
  numero_operacion_banco varchar,
  monto_sistema_centimos bigint NOT NULL DEFAULT 0,
  monto_banco_centimos bigint NOT NULL DEFAULT 0,
  diferencia_centimos bigint,
  estado varchar NOT NULL DEFAULT 'PENDIENTE',
  justificacion text,
  created_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_conciliacion_detalle_tipo CHECK (tipo_operacion IN (
    'COBRO','TRANSFERENCIA'
  )),
  CONSTRAINT ck_conciliacion_detalle_estado CHECK (estado IN (
    'PENDIENTE','CONCILIADO','JUSTIFICADO'
  )),
  CONSTRAINT ck_conciliacion_detalle_montos CHECK (
    monto_sistema_centimos >= 0 AND monto_banco_centimos >= 0
  ),
  CONSTRAINT ck_conciliacion_justificacion CHECK (
    estado <> 'JUSTIFICADO'
    OR NULLIF(TRIM(BOTH FROM justificacion), '') IS NOT NULL
  ),
  CONSTRAINT ck_conciliacion_detalle_origen CHECK (
    pago_id IS NULL OR orden_compra_id IS NULL
  )
);

CREATE INDEX IF NOT EXISTS idx_conciliacion_detalle_pago
  ON conciliacion_detalle (pago_id);
CREATE INDEX IF NOT EXISTS idx_conciliacion_detalle_compra
  ON conciliacion_detalle (orden_compra_id);
CREATE INDEX IF NOT EXISTS idx_conciliacion_detalle_conciliacion
  ON conciliacion_detalle (conciliacion_id);

CREATE OR REPLACE FUNCTION proteger_detalle_conciliacion()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  v_estado varchar(20);
  v_conciliacion_id uuid;
BEGIN
  IF TG_OP = 'UPDATE'
     AND NEW.conciliacion_id IS DISTINCT FROM OLD.conciliacion_id THEN
    RAISE EXCEPTION 'No se puede cambiar la conciliacion de un detalle';
  END IF;

  IF TG_OP = 'DELETE' THEN
    v_conciliacion_id := OLD.conciliacion_id;
  ELSE
    v_conciliacion_id := NEW.conciliacion_id;
  END IF;

  SELECT estado INTO v_estado
  FROM conciliaciones_bancarias
  WHERE id = v_conciliacion_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'La conciliacion no existe';
  END IF;

  IF v_estado = 'CERRADA' THEN
    RAISE EXCEPTION
      'No se pueden modificar detalles de una conciliacion cerrada';
  END IF;

  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;

  RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION validar_cierre_conciliacion()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.estado = 'CERRADA' THEN
    IF EXISTS (
      SELECT 1
      FROM conciliacion_detalle cd
      WHERE cd.conciliacion_id = NEW.id
        AND cd.estado = 'PENDIENTE'
    ) THEN
      RAISE EXCEPTION
        'No se puede cerrar la conciliacion: existen operaciones pendientes';
    END IF;

    IF NEW.fecha_cierre IS NULL THEN
      RAISE EXCEPTION 'La fecha de cierre es obligatoria';
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger WHERE tgname = 'trg_proteger_detalle_conciliacion'
  ) THEN
    CREATE TRIGGER trg_proteger_detalle_conciliacion
      BEFORE INSERT OR UPDATE OR DELETE ON conciliacion_detalle
      FOR EACH ROW EXECUTE FUNCTION proteger_detalle_conciliacion();
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger WHERE tgname = 'trg_validar_cierre_conciliacion'
  ) THEN
    CREATE TRIGGER trg_validar_cierre_conciliacion
      BEFORE UPDATE ON conciliaciones_bancarias
      FOR EACH ROW EXECUTE FUNCTION validar_cierre_conciliacion();
  END IF;
END $$;

-- ---------------------------------------------------------------------------
-- solicitudes_credito
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS solicitudes_credito (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  uuid_operacion uuid NOT NULL,
  administrador_id uuid NOT NULL REFERENCES usuarios(id),
  monto_solicitado_centimos bigint NOT NULL,
  moneda varchar NOT NULL DEFAULT 'PEN',
  periodo_inicio date NOT NULL,
  periodo_fin date NOT NULL,
  resumen_ventas jsonb NOT NULL,
  autorizacion_compartir_datos boolean NOT NULL,
  estado varchar NOT NULL DEFAULT 'ENVIADO',
  referencia_banco varchar,
  numero_operacion_desembolso varchar,
  monto_aprobado_centimos bigint,
  fecha_solicitud timestamptz NOT NULL DEFAULT now(),
  fecha_respuesta timestamptz,
  fecha_desembolso timestamptz,
  observaciones text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT solicitudes_credito_uuid_operacion_key UNIQUE (uuid_operacion),
  CONSTRAINT solicitudes_credito_referencia_banco_key UNIQUE (referencia_banco),
  CONSTRAINT solicitudes_credito_numero_operacion_desembolso_key
    UNIQUE (numero_operacion_desembolso),
  CONSTRAINT ck_credito_monto_solicitado CHECK (monto_solicitado_centimos > 0),
  CONSTRAINT ck_credito_moneda CHECK (moneda = 'PEN'),
  CONSTRAINT ck_credito_periodo CHECK (periodo_fin >= periodo_inicio),
  CONSTRAINT ck_credito_resumen CHECK (jsonb_typeof(resumen_ventas) = 'object'),
  CONSTRAINT ck_credito_autorizacion CHECK (autorizacion_compartir_datos = true),
  CONSTRAINT ck_credito_estado CHECK (estado IN (
    'ENVIADO','EN_EVALUACION','APROBADO','RECHAZADO','DESEMBOLSADO'
  )),
  CONSTRAINT ck_credito_monto_aprobado CHECK (
    monto_aprobado_centimos IS NULL OR monto_aprobado_centimos > 0
  ),
  CONSTRAINT ck_credito_aprobacion CHECK (
    estado <> ALL (ARRAY['APROBADO','DESEMBOLSADO']::varchar[])
    OR (monto_aprobado_centimos IS NOT NULL AND fecha_respuesta IS NOT NULL)
  ),
  CONSTRAINT ck_credito_desembolso CHECK (
    estado <> 'DESEMBOLSADO'
    OR (numero_operacion_desembolso IS NOT NULL AND fecha_desembolso IS NOT NULL)
  )
);

CREATE INDEX IF NOT EXISTS idx_credito_administrador
  ON solicitudes_credito (administrador_id);
CREATE INDEX IF NOT EXISTS idx_credito_estado ON solicitudes_credito (estado);
CREATE INDEX IF NOT EXISTS idx_credito_fecha
  ON solicitudes_credito (fecha_solicitud DESC);

COMMIT;
