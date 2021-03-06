package operato.logis.das.service.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.JobInstance;
import xyz.anythings.base.entity.Rework;
import xyz.anythings.base.entity.ReworkItem;
import xyz.anythings.base.service.util.LogisServiceUtil;
import xyz.elidom.orm.IQueryManager;
import xyz.elidom.sys.util.ValueUtil;
import xyz.elidom.util.BeanUtil;

/**
 * DAS 용 서비스 유틸리티
 * 
 * @author shortstop
 */
public class OperatoDasServiceUtil extends LogisServiceUtil {
	
	/**
	 * 재 작업 히스토리 기록
	 * 
	 * @param batch
	 * @param jobList
	 * @param skuCd
	 * @param skuNm
	 * @param reworkType 재작업 유형 : Rework.REWORK_TYPE_RELIGHT, Rework.REWORK_TYPE_INSPECTION
	 */
	public static void createReworkHistories(JobBatch batch, String skuCd, String skuNm, List<JobInstance> jobList, String reworkType) {
		IQueryManager queryMgr = BeanUtil.get(IQueryManager.class);
		
		// 1. 재작업 마스터 기록
		Rework rework = ValueUtil.populate(batch, new Rework());
		String sql = "select max(rework_seq) as rework_seq from reworks where domain_id = :domainId and batch_id = :batchId and rework_type = :reworkType";
		Map<String, Object> params = ValueUtil.newMap("domainId,batchId,reworkType", batch.getDomainId(), batch.getId(), reworkType);
		Integer maxSeq = queryMgr.selectBySql(sql, params, Integer.class);
		
		rework.setId(null);
		rework.setBatchId(batch.getId());
		rework.setReworkSeq(maxSeq == null ? 0 : maxSeq + 1);
		rework.setReworkCd("skuCd");
		rework.setInputCd(skuCd);
		rework.setInputNm(skuNm);
		rework.setReworkType(reworkType);
		rework.setReworkQty(ValueUtil.isEmpty(jobList) ? 0 : jobList.size());
		queryMgr.insert(rework);
		
		// 2. 재작업 상세 기록
		if(ValueUtil.isNotEmpty(jobList)) {
			List<ReworkItem> items = new ArrayList<ReworkItem>(jobList.size());
			for(JobInstance job : jobList) {
				ReworkItem item = ValueUtil.populate(job, new ReworkItem());
				item.setId(null);
				item.setReworkId(rework.getId());
				item.setEquipCd(job.getEquipCd());
				item.setSubEquipCd(job.getSubEquipCd());
				item.setClassCd(job.getClassCd());
				item.setSkuCd(job.getSkuCd());
				item.setSkuNm(job.getSkuNm());
				item.setReworkQty(job.getPickQty());
				items.add(item);
			}
			
			queryMgr.insertBatch(items);
		}
	}
	
	/**
	 * 재 작업 히스토리 기록
	 * 
	 * @param batch
	 * @param jobList
	 * @param reworkType 재작업 유형 : Rework.REWORK_TYPE_RELIGHT, Rework.REWORK_TYPE_INSPECTION
	 */
	public static void createReworkHistories(JobBatch batch, List<JobInstance> jobList, String reworkType) {
		if(ValueUtil.isNotEmpty(jobList)) {
			JobInstance firstJob = jobList.get(0);
			createReworkHistories(batch, firstJob.getSkuCd(), firstJob.getSkuNm(), jobList, reworkType);
		}
	}

}