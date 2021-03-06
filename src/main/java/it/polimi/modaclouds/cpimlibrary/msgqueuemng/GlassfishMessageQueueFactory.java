/**
 * Copyright 2013 deib-polimi
 * Contact: deib-polimi <marco.miglierina@polimi.it>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package it.polimi.modaclouds.cpimlibrary.msgqueuemng;

import java.util.HashMap;
import it.polimi.modaclouds.cpimlibrary.CloudMetadata;
import it.polimi.modaclouds.cpimlibrary.ModeQueue;
import it.polimi.modaclouds.cpimlibrary.QueueInfo;


public class GlassfishMessageQueueFactory extends CloudMessageQueueFactory {

	private HashMap<String, QueueInfo> info = null;

	public GlassfishMessageQueueFactory(CloudMetadata metadata) {
		//registra informazioni sulle code inserite per l'applicatione
		this.info = metadata.getQueueMedatada();

		
	}

	
	@Override
	public CloudMessageQueue getQueue(String queueName) {
		QueueInfo queueInfo = info.get(queueName);
		if (queueInfo.getMode().equals(ModeQueue.PULL))
			return new GlassfishMessageQueue(queueName, queueInfo.getMessageQueueConnection(),queueInfo.getMessageQueueResource());
		try {
			throw new CloudMessageQueueException("Wrong Mode...Please check your configurations in queue.xml");
		
		} catch (CloudMessageQueueException e) {
			e.printStackTrace();
			return null;
		}
	
	}

}
