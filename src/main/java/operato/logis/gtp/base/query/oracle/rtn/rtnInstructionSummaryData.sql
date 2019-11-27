SELECT
       CASE
              WHEN RACK_TYPE = 'P'
              THEN SKU_CNT/2
              ELSE SKU_CNT
       END SKU_CNT,
       CASE
              WHEN RACK_TYPE = 'P'
              THEN CUST_QTY/2
              ELSE CUST_QTY
       END CUST_CNT,
       CASE
              WHEN RACK_TYPE = 'P'
              THEN PCS_QTY/2
              ELSE PCS_QTY
       END PCS_CNT
FROM   ( 
	SELECT  RACK_TYPE,
            COUNT(1) AS SKU_CNT,
            (SELECT COUNT(DISTINCT(COM_CD))
            FROM    ORDER_PREPROCESSES
            WHERE   BATCH_ID = :batchId
            ) AS CUST_QTY,
            SUM(TOTAL_PCS) AS PCS_QTY
   FROM     ( SELECT X.CELL_ASSGN_CD ,
                    Y.RACK_TYPE,
                    X.SKU_QTY,
                    X.TOTAL_PCS
            FROM    ORDER_PREPROCESSES X,
                    ( SELECT RACK_CD,
                            RACK_TYPE
                    FROM    RACKS
                    WHERE   DOMAIN_ID = :domainId
                    ) Y
            WHERE   X.EQUIP_CD  = Y.RACK_CD
            AND     X.DOMAIN_ID = :domainId
            AND     X.BATCH_ID  = :batchId
            )
   GROUP BY RACK_TYPE
)
