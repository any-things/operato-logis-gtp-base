UPDATE ORDER_PREPROCESSES
SET    EQUIP_CD = NULL,
       EQUIP_NM = NULL,
       SUB_EQUIP_CD = NULL 
WHERE  DOMAIN_ID = :domainId
AND    BATCH_ID  = :batchId
#if($equipCds)
AND    EQUIP_CD  IN (:equipCds)  
#end
 