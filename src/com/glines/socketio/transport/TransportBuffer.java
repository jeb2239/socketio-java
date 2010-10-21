package com.glines.socketio.transport;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class TransportBuffer {
	public interface BufferListener {
		/**
		 * @param message
		 * @return false if message send timed-out or failed
		 */
		boolean onMessage(String message);
		boolean onMessages(List<String> messages);
	}
	
	private final int bufferSize;
	private final Semaphore inputSemaphore;
	private final BlockingQueue<String> queue = new LinkedBlockingQueue<String>();
	private AtomicReference<BufferListener> listenerRef = new AtomicReference<BufferListener>();
	
	public TransportBuffer(int bufferSize) {
		this.bufferSize = bufferSize;
		this.inputSemaphore = new Semaphore(bufferSize);
	}

	public void setListener(BufferListener listener) {
		this.listenerRef.set(listener);
	}
	
	public int getBufferSize() {
		return bufferSize;
	}

	public int getAvailableBytes() {
		return bufferSize - inputSemaphore.availablePermits();
	}

	public int getFreeBytes() {
		return inputSemaphore.availablePermits();
	}

	public List<String> drainMessages() {
		List<String> list = new ArrayList<String>();
		queue.drainTo(list);

		for (String str: list) {
			inputSemaphore.release(str.length());
		}
		
		return list;
	}
	
	public String getMessage(long timeout) {
		try {
			String msg = queue.poll(timeout, TimeUnit.MILLISECONDS);
			if (msg != null) {
				inputSemaphore.release(msg.length());
			}
			return msg;
		} catch (InterruptedException e) {
			return null;
		}
	}

	public boolean flush() {
		BufferListener listener = listenerRef.get();
		if (listener != null) {
			try {
				if (queue.size() != 0) {
					ArrayList<String> messages = new ArrayList<String>(queue.size());
					queue.drainTo(messages);
					return listener.onMessages(messages);
				}
			} catch (Throwable t) {
				return false;
			}
		}
		return true;
	}
	
	public boolean putMessage(String message, long timeout) {
		BufferListener listener = listenerRef.get();
		if (listener != null) {
			try {
				if (queue.size() == 0) {
					return listener.onMessage(message);
				} else {
					ArrayList<String> messages = new ArrayList<String>(queue.size()+1);
					queue.drainTo(messages);
					messages.add(message);
					return listener.onMessages(messages);
				}
			} catch (Throwable t) {
				return false;
			}
		} else {
			try {
				inputSemaphore.tryAcquire(message.length(), timeout, TimeUnit.MILLISECONDS);
				queue.offer(message);
				return true;
			} catch (InterruptedException e) {
				return false;
			}
		}
	}
}