SELECT 
	*
FROM (
	SELECT
		I.ID, I.INPUT_SEQ, I.SKU_CD, J.SKU_NM, I.STATUS, I.INPUT_QTY, SUM(J.PICK_QTY) AS PLAN_QTY, SUM(J.PICKED_QTY) AS RESULT_QTY
	FROM
		JOB_INPUTS I INNER JOIN JOB_INSTANCES J ON I.DOMAIN_ID = J.DOMAIN_ID AND I.BATCH_ID = J.BATCH_ID AND I.INPUT_SEQ = J.INPUT_SEQ
	WHERE
	 	I.DOMAIN_ID = :domainId
	 	AND I.BATCH_ID = :batchId
	   #if($equipCd)
	 	AND J.EQUIP_CD = :equipCd
	   #end
	   #if($stationCd)
	 	AND I.STATION_CD = :stationCd
	   #end
	   #if($inputSeq)
	   	#if($inputSeq <= 4)
	 	AND I.INPUT_SEQ <= :inputSeq
	 	#else
	 	AND (I.INPUT_SEQ >= :inputSeq - 1 AND I.INPUT_SEQ <= :inputSeq + 3) 
	 	#end
	   #end
	GROUP BY
		I.ID, I.INPUT_SEQ, I.SKU_CD, J.SKU_NM, I.STATUS, I.INPUT_QTY
	ORDER BY
		I.INPUT_SEQ ASC
) WHERE
	ROWNUM <= 4