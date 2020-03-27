package operato.logis.das.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import xyz.anythings.base.entity.JobBatch;
import xyz.anythings.base.entity.OrderPreprocess;
import xyz.anythings.base.service.api.IPreprocessService;
import xyz.anythings.sys.service.AbstractExecutionService;
import xyz.elidom.dbist.dml.Query;

/**
 * 출고 주문 가공 서비스
 * 
 * @author shortstop
 */
@Component("dasPreprocessService")
public class DasPreprocessService extends AbstractExecutionService implements IPreprocessService {

	@Override
	public List<OrderPreprocess> searchPreprocessList(JobBatch batch) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, ?> buildPreprocessSet(JobBatch batch, Query query) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int generatePreprocess(JobBatch batch) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int deletePreprocess(JobBatch batch) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public List<JobBatch> completePreprocess(JobBatch batch, Object... params) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void resetPreprocess(JobBatch batch, boolean isRackReset, List<String> equipCdList) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public int assignEquipLevel(JobBatch batch, String equipCds, List<OrderPreprocess> items, boolean automatically) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int assignSubEquipLevel(JobBatch batch, String equipType, String equipCd, List<OrderPreprocess> items) {
		// TODO Auto-generated method stub
		return 0;
	}

}
