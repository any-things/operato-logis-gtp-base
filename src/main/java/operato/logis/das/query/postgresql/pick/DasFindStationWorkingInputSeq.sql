SELECT
	MIN(INPUT_SEQ)
FROM
	JOB_INSTANCES
WHERE
	DOMAIN_ID = :domainId
	#if($batchId)
	AND BATCH_ID = :batchId
	#end
	#if($jobInputId)
	AND INPUT_SEQ = (SELECT INPUT_SEQ FROM JOB_INPUTS WHERE DOMAIN_ID = :domainId AND ID = :jobInputId)
	#else
	AND INPUT_SEQ > 0
	#end
	#if($stationCd)
	AND SUB_EQUIP_CD IN (SELECT CELL_CD FROM CELLS WHERE DOMAIN_ID = :domainId AND STATION_CD = :stationCd)
	#end
	AND STATUS = :jobStatus