package org.math.R;

import org.slf4j.LoggerFactory;

public class RLogSlf4j implements RLog {

	@Override
	public void log(String text, Level level){
		if(Level.OUTPUT.equals(level)){
			LOGGER.debug(text);
		}else if(Level.INFO.equals(level)){
			LOGGER.info(text);
		}else if(Level.WARNING.equals(level)){
			LOGGER.warn(text);
		}else if(Level.ERROR.equals(level)){
			LOGGER.error(text);
		}else 
			LOGGER.trace(text);
	}

	@Override
	public void close() {
		          
	}
        
	public final static org.slf4j.Logger LOGGER = LoggerFactory.getLogger("RLogger");
	
}