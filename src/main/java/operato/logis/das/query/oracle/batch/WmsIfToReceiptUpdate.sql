UPDATE WMS_IF_ORDERS
SET
	IF_FLAG = 'Y'
WHERE 
 	  STAGE_CD =:stageCd
  AND JOB_DATE = :jobDate
  
  #if($jobSeq)
  AND JOB_SEQ= :jobSeq 
  #end
  
  #if($wmsBatchNo)
  AND WMS_BATCH_NO =:wmsBatchNo
  #end
  
  #if($wcsBatchNo)
  AND WCS_BATCH_NO =:wcsBatchNo
  #end
