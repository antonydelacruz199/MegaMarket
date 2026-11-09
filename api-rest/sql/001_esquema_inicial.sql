-- Solo para una base NUEVA y vacía. En instalaciones existentes ejecutar únicamente 002.
BEGIN;
CREATE TABLE usuarios (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), nombre text NOT NULL,
 usuario text NOT NULL UNIQUE, correo text NOT NULL, clave_hash text NOT NULL,
 rol text NOT NULL CHECK(rol IN ('CLIENTE','ADMINISTRADOR')),
 version bigint NOT NULL DEFAULT 1, created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz
);
CREATE TABLE categorias (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), nombre text NOT NULL UNIQUE,
 version bigint NOT NULL DEFAULT 1, created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz
);
CREATE TABLE productos (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), nombre text NOT NULL CHECK(trim(nombre)<>''),
 marca text NOT NULL, descripcion text, categoria_id uuid NOT NULL REFERENCES categorias(id),
 precio_centimos bigint NOT NULL CHECK(precio_centimos>0), precio_oferta_centimos bigint,
 stock integer NOT NULL CHECK(stock>=0), imagen_key text,
 es_oferta boolean NOT NULL DEFAULT false, activo boolean NOT NULL DEFAULT true,
 version bigint NOT NULL DEFAULT 1, created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz,
 CHECK ((NOT es_oferta AND precio_oferta_centimos IS NULL) OR
        (es_oferta AND precio_oferta_centimos IS NOT NULL AND precio_oferta_centimos>0 AND precio_oferta_centimos<precio_centimos))
);
CREATE TABLE pedidos (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), cliente_id uuid NOT NULL REFERENCES usuarios(id),
 client_uuid uuid NOT NULL UNIQUE, fecha timestamptz NOT NULL DEFAULT now(),
 total_centimos bigint NOT NULL CHECK(total_centimos>0), estado text NOT NULL,
 version bigint NOT NULL DEFAULT 1, created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz
);
CREATE TABLE pedido_detalle (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), pedido_id uuid NOT NULL REFERENCES pedidos(id),
 producto_id uuid NOT NULL REFERENCES productos(id), nombre_producto text NOT NULL,
 precio_unitario_centimos bigint NOT NULL CHECK(precio_unitario_centimos>0),
 cantidad integer NOT NULL CHECK(cantidad>0), subtotal_centimos bigint NOT NULL CHECK(subtotal_centimos>0),
 version bigint NOT NULL DEFAULT 1, created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz
);
CREATE TABLE direcciones (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), pedido_id uuid NOT NULL REFERENCES pedidos(id),
 departamento text NOT NULL, provincia text NOT NULL, distrito text NOT NULL,
 direccion text NOT NULL, telefono text NOT NULL,
 version bigint NOT NULL DEFAULT 1, created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz
);
CREATE TABLE movimientos_inventario (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), uuid_operacion uuid NOT NULL UNIQUE,
 producto_id uuid NOT NULL REFERENCES productos(id), pedido_id uuid REFERENCES pedidos(id),
 usuario_id uuid NOT NULL REFERENCES usuarios(id),
 tipo text NOT NULL CHECK(tipo IN ('SALIDA_VENTA','AJUSTE_ENTRADA','AJUSTE_SALIDA')),
 cantidad integer NOT NULL CHECK(cantidad>0),
 version bigint NOT NULL DEFAULT 1, created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz
);
CREATE TABLE operaciones_idempotentes (
 uuid_operacion uuid PRIMARY KEY, usuario_id uuid NOT NULL REFERENCES usuarios(id),
 tipo_operacion text NOT NULL, recurso text NOT NULL, entidad_id uuid,
 estado text NOT NULL, codigo_http integer NOT NULL, respuesta jsonb NOT NULL
);
CREATE FUNCTION actualizar_version_y_fecha() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN NEW.version=OLD.version+1; NEW.updated_at=now(); RETURN NEW; END;
$$;
DO $$ DECLARE tabla text; BEGIN
 FOREACH tabla IN ARRAY ARRAY['usuarios','categorias','productos','pedidos','pedido_detalle','direcciones','movimientos_inventario'] LOOP
  EXECUTE format('CREATE TRIGGER actualizar_version BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION actualizar_version_y_fecha()',tabla);
 END LOOP;
END; $$;
COMMIT;
