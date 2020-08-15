SELECT 
	A.ID, A.INPUT_SEQ, A.SKU_CD, A.SKU_NM, A.COLOR_CD, A.INPUT_QTY, A.PLAN_QTY, A.RESULT_QTY, CASE WHEN A.PLAN_QTY > A.RESULT_QTY THEN 'R' ELSE 'F' END AS STATUS
FROM (
	SELECT
		I.ID, I.INPUT_SEQ, I.SKU_CD, J.SKU_NM, I.COLOR_CD, I.INPUT_QTY, SUM(J.PICK_QTY) AS PLAN_QTY, SUM(J.PICKED_QTY) AS RESULT_QTY
	FROM
		JOB_INPUTS I INNER JOIN JOB_INSTANCES J ON I.DOMAIN_ID = J.DOMAIN_ID AND I.BATCH_ID = J.BATCH_ID AND I.INPUT_SEQ = J.INPUT_SEQ
	WHERE
		I.DOMAIN_ID = :domainId
		AND I.BATCH_ID = :batchId
		#if($equipCd)
		AND J.EQUIP_CD = :equipCd
		#end
		#if($stationCd)
		AND J.SUB_EQUIP_CD IN (SELECT CELL_CD FROM CELLS WHERE DOMAIN_ID = :domainId AND STATION_CD = :stationCd)
		#end
		#if($inputSeq)
			#if($inputSeq == 1)
			AND I.INPUT_SEQ >= 1
			#else
			AND I.INPUT_SEQ >= :inputSeq - 1
			#end
		#end
	GROUP BY
		I.ID, I.INPUT_SEQ, I.SKU_CD, J.SKU_NM, I.COLOR_CD, I.INPUT_QTY
	ORDER BY
		I.INPUT_SEQ
		#if($lastFour)
		DESC
		#else
		ASC
		#end
) A
	LIMIT 4