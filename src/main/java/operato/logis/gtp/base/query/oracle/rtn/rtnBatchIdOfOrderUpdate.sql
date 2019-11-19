UPDATE ORDERS
SET BATCH_ID = :newBatchId,
    JOB_SEQ = :jobBatchSeq,
    EQUIP_CD = :equipCd,
    UPDATER_ID = :userId,
    UPDATED_AT = SYSDATE
WHERE BATCH_ID = :batchId AND SKU_CD IN (
		SELECT CELL_ASSGN_CD FROM ORDER_PREPROCESSES 
		#if()
		WHERE BATCH_ID = :newBatchId AND (EQUIP_CD IS NULL OR EQUIP_CD = '' )
		#else
		WHERE BATCH_ID = :newBatchId AND EQUIP_CD = :equipCd);
		#end
)