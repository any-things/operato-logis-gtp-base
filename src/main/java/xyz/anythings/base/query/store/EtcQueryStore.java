package xyz.anythings.base.query.store;

import org.springframework.stereotype.Component;

/**
 * 기타 쿼리 스토어 
 * 
 * @author shortstop
 */
@Component
public class EtcQueryStore extends AbstractQueryStore {

	/**
	 * 데이터베이스 서버 기준 현재 시간을 조회하는 쿼리 
	 * 
	 * @return
	 */
	public String getCurrentTimeQuery() {
		return this.getQueryByPath("etc/CurrentTime");
	}
	
}
