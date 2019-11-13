SELECT *
  FROM BATCH_RECEIPTS
 WHERE DOMAIN_ID = :domainId
   AND COM_CD = :comCd
   AND AREA_CD = :areaCd
   AND STAGE_CD = :stageCd
   AND JOB_DATE = :jobDate
   AND STATUS in ( :status )
   AND ID in ( SELECT DISTINCT BATCH_RECEIPT_ID
                 FROM BATCH_RECEIPT_ITEMS
                WHERE DOMAIN_ID = :domainId
                  AND COM_CD = :comCd
                  AND AREA_CD = :areaCd 
                  AND STAGE_CD = :stageCd
                  AND STATUS in ( :status )
                  AND ITEM_TYPE = 'order' 
                  AND JOB_TYPE = :jobType
		   )
   AND ROWNUM = 1 