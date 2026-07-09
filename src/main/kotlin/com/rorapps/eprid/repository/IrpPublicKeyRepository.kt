package com.rorapps.eprid.repository

import com.rorapps.eprid.constants.EInvoiceIrp
import com.rorapps.eprid.entity.IrpPublicKey
import org.springframework.data.jpa.repository.JpaRepository

interface IrpPublicKeyRepository : JpaRepository<IrpPublicKey, String> {
    fun findAllByIrpAndActiveTrue(irp: EInvoiceIrp): List<IrpPublicKey>
    fun findAllByActiveTrue(): List<IrpPublicKey>
    fun findAllByKeyId(keyId: String): List<IrpPublicKey>
}
