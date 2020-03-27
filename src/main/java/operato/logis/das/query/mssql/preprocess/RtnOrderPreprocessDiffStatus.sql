SELECT *
FROM   ( SELECT NVL(ORD.ORD_PCS_QTY, 0) AS ORDER_SKU_QTY,
               NVL(PP.PRE_PCS_QTY, 0)   AS ORDER_PCS_QTY
       FROM    ( SELECT  SKU_CD,
                        SUM(ORDER_QTY) AS ORD_PCS_QTY
               FROM     ORDERS
               WHERE    DOMAIN_ID = :domainId
               AND      BATCH_ID  = :batchId 
               GROUP BY SKU_CD
               ORDER BY SKU_CD
               ) ORD $outerJoinDiretion OUTER JOIN
               ( SELECT  CELL_ASSGN_CD,
                        SUM(TOTAL_PCS) AS PRE_PCS_QTY
               FROM     ORDER_PREPROCESSES
               WHERE    DOMAIN_ID = :domainId
               AND      BATCH_ID  = :batchId 
               GROUP BY CELL_ASSGN_CD
               ORDER BY CELL_ASSGN_CD 
               ) PP
               ON      ORD.SKU_CD = PP.CELL_ASSGN_CD
       ) DIFF
WHERE  DIFF.ORDER_SKU_QTY != DIFF.ORDER_PCS_QTY
