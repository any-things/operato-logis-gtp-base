UPDATE WMS_IF_ORDERS
SET
	IF_FLAG = 'Y'
WHERE COM_CD = :comCd
  AND AREA_CD= :areaCd 
  AND STAGE_CD =:stageCd
  AND JOB_TYPE =:jobType
  AND JOB_SEQ= :jobSeq 
  #if($wmsBatchNo)
  AND WMS_BATCH_NO =:wmsBatchNo
  #end
  #if($wcsBatchNo)
  AND WCS_BATCH_NO =:wcsBatchNo
  #end
  