package xyz.anythings.base.query.store;

import org.springframework.stereotype.Component;

/**
 * 설정 관련 쿼리 스토어
 * - 세팅, 표시기, 작업 관련 쿼리 관리 
 * 
 * @author shortstop
 */
@Component
public class ConfigQueryStore extends AbstractQueryStore {


	/**
	 * 배치에 해당 하는 Job configsSet 생성
	 * 
	 * @return
	 */
	public String getBuildJobConfigSetQuery() {
		return this.getQueryByPath("config/BuildJobConfigSet");
	}
	
	
	/**
	 * 배치에 해당 하는 Ind configSet 생성
	 * 
	 * @return
	 */
	public String getBuildIndConfigSetQuery() {
		return this.getQueryByPath("config/BuildIndConfigSet");
	}

	
}
