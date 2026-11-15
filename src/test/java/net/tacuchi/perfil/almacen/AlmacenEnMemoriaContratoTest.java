package net.tacuchi.perfil.almacen;

import net.tacuchi.perfil.soporte.AlmacenEnMemoria;

class AlmacenEnMemoriaContratoTest extends ContratoAlmacen {

    @Override
    protected AlmacenArchivos crear() {
        return new AlmacenEnMemoria();
    }
}
