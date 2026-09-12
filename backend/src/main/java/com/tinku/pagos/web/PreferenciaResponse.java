package com.tinku.pagos.web;

import com.tinku.pagos.port.MercadoPagoClient.PreferenciaPago;

public record PreferenciaResponse(String preferenciaId, String initPoint, boolean bypass) {

    public static PreferenciaResponse from(PreferenciaPago preferencia) {
        return new PreferenciaResponse(
                preferencia.preferenceId(), preferencia.initPoint(), preferencia.bypass());
    }
}