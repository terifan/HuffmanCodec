package org.terifan.compression.cabac2;


public class Context
{
	int MPS;
	int state;


	public Context(int pstate)
	{
		if (pstate >= 64)
		{
			pstate = Math.min(126, pstate);
			state = (pstate - 64);
			MPS = 1;
		}
		else
		{
			pstate = Math.max(1, pstate);
			state = (63 - pstate);
			MPS = 0;
		}
	}
}
