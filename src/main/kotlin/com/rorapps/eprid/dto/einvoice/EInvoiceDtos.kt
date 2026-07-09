package com.rorapps.eprid.dto.einvoice

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.rorapps.eprid.constants.EInvoiceIrp
import com.rorapps.eprid.constants.InvoiceOriginalityStatus

/** Fields carried in the signed e-invoice QR JWT payload (PRD §7.1). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EInvoiceQrPayload(
    @JsonProperty("SellerGstin") val sellerGstin: String? = null,
    @JsonProperty("BuyerGstin") val buyerGstin: String? = null,
    @JsonProperty("DocNo") val docNo: String? = null,
    @JsonProperty("DocTyp") val docTyp: String? = null,
    @JsonProperty("DocDt") val docDt: String? = null,
    @JsonProperty("TotInvVal") val totInvVal: String? = null,
    @JsonProperty("ItemCnt") val itemCnt: String? = null,
    @JsonProperty("MainHsnCode") val mainHsnCode: String? = null,
    @JsonProperty("Irn") val irn: String? = null,
    @JsonProperty("IrnDt") val irnDt: String? = null
)

data class InvoiceOriginalityResult(
    val status: InvoiceOriginalityStatus,
    val irp: EInvoiceIrp?,
    val reason: String,
    val payload: EInvoiceQrPayload? = null
)
