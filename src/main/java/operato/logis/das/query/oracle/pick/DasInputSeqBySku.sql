SELECT 
	NVL(MAX(INPUT_SEQ), -1) AS INPUT_SEQ 
FROM 
	JOB_INPUTS
WHERE 
	DOMAIN_ID = :domainId 
	AND BATCH_ID = :batchId 
	AND COM_CD = :comCd 
	AND SKU_CD = :skuCd
	AND EQUIP_TYPE = 'RACK'
	#if($equipCd)
	AND EQUIP_CD = :equipCd
	#end