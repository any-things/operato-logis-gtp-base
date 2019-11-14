SELECT RACK_CD, 
	   RACK_NM,
       ASSIGNED_CELLS,
       (TOTAL_CELLS - ASSIGNED_CELLS) AS REMAIN_CELLS,
       ASSIGNED_SKU,
       ASSIGNED_PCS
FROM   ( 	
  SELECT    A.RACK_CD,
            A.RACK_NM,
            A.TOTAL_CELLS,
            NVL(B.ASSIGNED_CELLS, 0) AS ASSIGNED_CELLS,
            NVL(B.ASSIGNED_SKU, 0)   AS ASSIGNED_SKU,
            NVL(B.ASSIGNED_PCS, 0)   AS ASSIGNED_PCS
   FROM     (
		   		SELECT  REG.RACK_CD,
	                     REG.RACK_NM,
	                     CASE
	                              WHEN REG.RACK_TYPE = 'P'
	                              THEN FLOOR(COUNT(LOC.EQUIP_CD) / 2)
	                              ELSE COUNT(LOC.EQUIP_CD)
	                     END AS TOTAL_CELLS
	            FROM     CELLS  LOC
	                     INNER JOIN RACKS REG
	                     ON       LOC.DOMAIN_ID = REG.DOMAIN_ID
	                     AND      LOC.EQUIP_CD =  REG.RACK_CD
	            WHERE    LOC.DOMAIN_ID          = :domainId
	            AND		 REG.STAGE_CD	 		= :stageCd     
	            AND      REG.DOMAIN_ID          = LOC.DOMAIN_ID
	            AND      LOC.ACTIVE_FLAG        = :activeFlag                         
	            GROUP BY REG.RACK_CD,
	                     REG.RACK_NM,
	                     REG.RACK_TYPE
            ) A
            LEFT OUTER JOIN
            (
            	SELECT  EQUIP_CD,
	                      COUNT(DISTINCT(CELL_ASSGN_CD)) AS ASSIGNED_CELLS,
	                      0                       AS ASSIGNED_SKU,
	                      SUM(TOTAL_PCS)          AS ASSIGNED_PCS
	             FROM     ORDER_PREPROCESSES 
	             WHERE    DOMAIN_ID = :domainId
	             AND      BATCH_ID  = :batchId
	             GROUP BY EQUIP_CD
             ) B
            ON       A.RACK_CD = B.EQUIP_CD
   ORDER BY A.RACK_CD
)