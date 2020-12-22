WITH T_JOBS AS (
    SELECT CLASS_CD, COM_CD, SKU_CD
         , PICK_QTY, PICKED_QTY
         , PICK_QTY - PICKED_QTY AS MINUS_QTY
      FROM (
            SELECT CLASS_CD, COM_CD, SKU_CD
                 , PICK_QTY, COALESCE(PICKED_QTY, 0) AS PICKED_QTY
              FROM JOB_INSTANCES
             WHERE DOMAIN_ID = :domainId
               AND BATCH_ID IN (SELECT ID FROM JOB_BATCHES WHERE DOMAIN_ID = :domainId AND BATCH_GROUP_ID = :batchGroupId)
               AND STATUS != 'D' -- 주문 취소가 아닌 것 
           ) A
),
T_PCS AS (
    SELECT SUM(PICK_QTY) AS PLAN_PCS, SUM(PICKED_QTY) AS ACTUAL_PCS
      FROM T_JOBS
),
T_SKU AS (
    SELECT COUNT(1) PLAN_SKU, SUM(ACTUAL_SKU) AS ACTUAL_SKU
      FROM (
            SELECT COM_CD, SKU_CD, CASE WHEN SUM(PICKED_QTY) = SUM(PICK_QTY) THEN 1 ELSE 0 END AS ACTUAL_SKU
              FROM T_JOBS
             GROUP BY COM_CD, SKU_CD
           ) A
),
T_ORDER AS (
    SELECT COUNT(1) PLAN_ORDER, SUM(ACTUAL_ORDER) AS ACTUAL_ORDER
      FROM (
            SELECT CLASS_CD, CASE WHEN SUM(PICKED_QTY) = SUM(PICK_QTY) THEN 1 ELSE 0 END AS ACTUAL_ORDER
              FROM T_JOBS
             GROUP BY CLASS_CD
           ) A
)
SELECT PLAN_PCS, ACTUAL_PCS, PLAN_SKU, ACTUAL_SKU, PLAN_ORDER, ACTUAL_ORDER
     , CASE WHEN PLAN_PCS = 0 THEN 0 ELSE ROUND(ACTUAL_PCS/PLAN_PCS, 3) * 100 END AS RATE_PCS
     , CASE WHEN PLAN_SKU = 0 THEN 0 ELSE ROUND(ACTUAL_SKU/PLAN_SKU, 3) * 100 END AS RATE_SKU
     , CASE WHEN PLAN_ORDER = 0 THEN 0 ELSE ROUND(ACTUAL_ORDER/PLAN_ORDER, 3) * 100 END AS RATE_ORDER
  FROM (
		SELECT SUM(PLAN_PCS) AS PLAN_PCS
		     , SUM(ACTUAL_PCS) AS ACTUAL_PCS
		     , SUM(PLAN_SKU) AS PLAN_SKU
		     , SUM(ACTUAL_SKU) AS ACTUAL_SKU
		     , SUM(PLAN_ORDER) AS PLAN_ORDER
		     , SUM(ACTUAL_ORDER) AS ACTUAL_ORDER
		  FROM (
		        SELECT PLAN_PCS, ACTUAL_PCS
		             , 0 AS PLAN_SKU, 0 AS ACTUAL_SKU
		             , 0 AS PLAN_ORDER, 0 AS ACTUAL_ORDER
		          FROM T_PCS 
		         UNION ALL
		        SELECT 0 AS PLAN_PCS, 0 AS ACTUAL_PCS
		             , PLAN_SKU, ACTUAL_SKU
		             , 0 AS PLAN_ORDER, 0 AS ACTUAL_ORDER
		          FROM T_SKU 
		         UNION ALL
		        SELECT 0 AS PLAN_PCS, 0 AS ACTUAL_PCS
		             , 0 AS PLAN_SKU, 0 AS ACTUAL_SKU
		             , PLAN_ORDER, ACTUAL_ORDER
		          FROM T_ORDER 
		       ) X
       ) X
