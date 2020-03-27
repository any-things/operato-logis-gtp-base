
SELECT 
	RESULT_ORDER_QTY,
	RESULT_QTY,
	(PICKED_QTY / PICK_QTY) PROGRESS_RATE,
	ROUND((PICK_ENDED_AT - PICK_STARTED_AT) * 24, 2) JOB_UPH
FROM ( 
	SELECT 
		COUNT(SKU_CD) RESULT_ORDER_QTY,
		SUM(PICK_QTY) PICK_QTY,
		SUM(PICKED_QTY) RESULT_QTY,
		TO_DATE(REPLACE(MIN(PICK_ENDED_AT), '-', ''), 'YYYYMMDD HH24:MI:SS') PICK_ENDED_AT,
		TO_DATE(REPLACE(MIN(PICK_STARTED_AT), '-', ''), 'YYYYMMDD HH24:MI:SS') PICK_STARTED_AT
	FROM    
		JOB_INSTANCES a
	WHERE   
		DOMAIN_ID = :domainId
		AND BATCH_ID = :batchId
)