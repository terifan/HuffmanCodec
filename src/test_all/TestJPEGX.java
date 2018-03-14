package test_all;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import static java.lang.Math.min;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;
import org.terifan.compression.cabac.CabacContext;
import org.terifan.compression.cabac.CabacDecoder;
import org.terifan.compression.cabac.CabacEncoder;


public class TestJPEGX
{
	public final static int[] NATURAL_ORDER =
	{
		0, 1, 8, 16, 9, 2, 3, 10,
		17, 24, 32, 25, 18, 11, 4, 5,
		12, 19, 26, 33, 40, 48, 41, 34,
		27, 20, 13, 6, 7, 14, 21, 28,
		35, 42, 49, 56, 57, 50, 43, 36,
		29, 22, 15, 23, 30, 37, 44, 51,
		58, 59, 52, 45, 38, 31, 39, 46,
		53, 60, 61, 54, 47, 55, 62, 63
	};

	public final static int[] ZIGZAG_ORDER =
	{
		0, 1, 5, 6, 14, 15, 27, 28,
		2, 4, 7, 13, 16, 26, 29, 42,
		3, 8, 12, 17, 25, 30, 41, 43,
		9, 11, 18, 24, 31, 40, 44, 53,
		10, 19, 23, 32, 39, 45, 52, 54,
		20, 22, 33, 38, 46, 51, 55, 60,
		21, 34, 37, 47, 50, 56, 59, 61,
		35, 36, 48, 49, 57, 58, 62, 63
	};


	public TestJPEGX(int[][][] aInputCoefficients) throws IOException
	{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		encode(baos, aInputCoefficients);

		byte[] data = baos.toByteArray();

		System.out.println(data.length);

		int[][][] outputCoefficients = new int[aInputCoefficients.length][aInputCoefficients[0].length][64];

		decode(new ByteArrayInputStream(data), outputCoefficients);

		for (int mcuIndex = 0; mcuIndex < outputCoefficients.length; mcuIndex++)
		{
			for (int blockIndex = 0; blockIndex < outputCoefficients[0].length; blockIndex++)
			{
				if (!Arrays.equals(aInputCoefficients[mcuIndex][blockIndex], outputCoefficients[mcuIndex][blockIndex]))
				{
//					System.out.println();
//					for (int i = 0; i < 64; i++)
//					{
//						/*if (aInputCoefficients[mcuIndex][blockIndex][i]!=0)*/ System.out.printf("%5d ", aInputCoefficients[mcuIndex][blockIndex][NATURAL_ORDER[i]]);
//					}
//					System.out.println();
//
//					for (int i = 0; i < 64; i++)
//					{
//						/*if (outputCoefficients[mcuIndex][blockIndex][i]!=0)*/ System.out.printf("%5d ", outputCoefficients[mcuIndex][blockIndex][NATURAL_ORDER[i]]);
//					}
//					System.out.println();
				}
			}
		}

		System.out.println(Arrays.deepEquals(aInputCoefficients, outputCoefficients));
	}

	static class State
	{
		CabacContext dclow = new CabacContext(0);
		CabacContext acsign = new CabacContext(0);
		CabacContext stop = new CabacContext(0);
		CabacContext dczero = new CabacContext(0);
		CabacContext[] run = new CabacContext[65];
		CabacContext[] acmag = new CabacContext[10000];
		CabacContext[] dc = new CabacContext[1000];
		CabacContext[] dcmag = new CabacContext[1000];
		CabacContext[][] ac = new CabacContext[64][1000];
		CabacContext[][] ac2 = new CabacContext[64][1000];


		public State()
		{
			for (int i = 0; i < run.length; i++) run[i] = new CabacContext(0);
			for (int i = 0; i < acmag.length; i++) acmag[i] = new CabacContext(0);
			for (int i = 0; i < dc.length; i++) dc[i] = new CabacContext(0);
			for (int i = 0; i < dcmag.length; i++) dcmag[i] = new CabacContext(0);
			for (int j = 0; j < ac.length; j++) for (int i = 0; i < ac[j].length; i++) ac[j][i] = new CabacContext(0);
			for (int j = 0; j < ac2.length; j++) for (int i = 0; i < ac2[j].length; i++) ac2[j][i] = new CabacContext(0);
		}
	}

	private void encode(ByteArrayOutputStream baos, int[][][] aCoefficients) throws IOException
	{
		int blockCount = aCoefficients[0].length;

		int[] lastdc = new int[3];
		int[] compLookup = {0,0,0,0,1,2};

		CabacEncoder encoder = new CabacEncoder(baos);

		State[] states = {new State(), new State(), new State()};

		for (int mcuIndex = 0; mcuIndex < aCoefficients.length; mcuIndex++)
		{
			for (int blockIndex = 0; blockIndex < blockCount; blockIndex++)
			{
				int ci = compLookup[blockIndex];
				int[] block = aCoefficients[mcuIndex][blockIndex];
				State st = states[ci];

				{
					int coefficient = block[0] - lastdc[ci];

					if (true)
					{
						encoder.encodeExpGolomb(encodeZigZag32(coefficient), 0, st.dcmag, st.dc);
					}
					else
					{
						encoder.encodeBit(coefficient == 0 ? 1 : 0, st.dczero);

						if (coefficient != 0)
						{
							boolean neg = coefficient < 0;
							if (neg)
							{
								coefficient = -coefficient;
							}

							coefficient--;

							int S = 10;
							int i = 0;
							while (coefficient >= S)
							{
								encoder.encodeBit(0, st.dcmag[i]);
								coefficient -= S;
//								i++;
							}
							encoder.encodeBit(1, st.dcmag[i]);

							i = 0;
							while (coefficient > 0)
							{
								encoder.encodeBit(0, st.dc[i]);
								coefficient--;
								i++;
							}
							encoder.encodeBit(1, st.dc[i]);

							encoder.encodeBitEqProb(neg ? 1 : 0);
						}
					}

					lastdc[ci] = block[0];
				}

				int ke = 63;

				do
				{
					if (block[NATURAL_ORDER[ke]] != 0)
					{
						break;
					}
				}
				while (--ke != 0);

				for (int pixel = 1; pixel <= ke; pixel++)
				{
					encoder.encodeBit(0, st.stop);

					int i = pixel;
					while (pixel < ke)
					{
						if (block[NATURAL_ORDER[pixel]] != 0)
						{
							break;
						}
						encoder.encodeBit(0, st.run[i]);
						pixel++;
						i++;
					}

					encoder.encodeBit(1, st.run[i]);

					int coefficient = block[NATURAL_ORDER[pixel]];

					boolean neg = coefficient < 0;

					if (neg)
					{
						coefficient = -coefficient;
					}

					coefficient--;

					if (false)
					{
						i = 0;
						int v = coefficient;
						int m = 1;
						while (v > 0)
						{
							encoder.encodeBit(0, st.acmag[i]);
							v >>= 1;
							m <<= 1;
							i++;
						}
						encoder.encodeBit(1, st.acmag[i]);

						i = 0;
						CabacContext[] ctx = st.ac[pixel / 5];
//						CabacContext[] ctx = st.ac[0];
						while ((m>>=1)!=0)
						{
//							int b = (coefficient & (1 << i)) != 0 ? 1 : 0;
							int b = (coefficient & m) != 0 ? 1 : 0;
							encoder.encodeBit(b, ctx[i]);
//							i--;
							i++;
//							i+=m>>1;
						}
					}
					else if (true)
					{
						encoder.encodeExpGolomb(coefficient, 0, st.ac[pixel], st.ac2[pixel]);
					}
					else
					{
						i = 0;
						CabacContext[] ctx = st.ac[pixel / 5];
						while (coefficient > 0)
						{
							encoder.encodeBit(0, ctx[i]);
							coefficient--;
							i++;
						}
						encoder.encodeBit(1, ctx[i]);
					}

					encoder.encodeBit(neg ? 1 : 0, st.acsign);
				}

				if (ke < 64)
				{
					encoder.encodeBit(1, st.stop);
				}
			}
		}

		encoder.encodeFinal(1);

		encoder.stopEncoding();
	}


	private void decode(ByteArrayInputStream bais, int[][][] aCoefficients) throws IOException
	{
		int blockCount = aCoefficients[0].length;

		int[] lastdc = new int[3];
		int[] compLookup = {0,0,0,0,1,2};

		CabacDecoder decoder = new CabacDecoder(new PushbackInputStream(bais));

		State[] states = {new State(), new State(), new State()};

		for (int mcuIndex = 0; mcuIndex < aCoefficients.length; mcuIndex++)
		{
			for (int blockIndex = 0; blockIndex < blockCount; blockIndex++)
			{
				int ci = compLookup[blockIndex];
				int[] block = aCoefficients[mcuIndex][blockIndex];
				State st = states[ci];

				if (true)
				{
					block[0] = lastdc[ci] = lastdc[ci] + decodeZigZag32((int)decoder.decodeExpGolomb(0, st.dcmag, st.dc));
				}
				else
				{
					if (decoder.decodeBit(st.dczero) == 1)
					{
						block[0] = lastdc[ci] = lastdc[ci] + 0;
					}
					else
					{
						int coefficient = 1;

						int S = 10;
						int i = 0;

						while (decoder.decodeBit(st.dcmag[i]) == 0)
						{
							coefficient += S;
//							i++;
						}

						i = 0;
						while (decoder.decodeBit(st.dc[i]) == 0)
						{
							coefficient++;
							i++;
						}

						block[0] = lastdc[ci] = lastdc[ci] + (decoder.decodeBitEqProb() == 1 ? -coefficient : coefficient);
					}
				}

				for (int pixel = 1;; pixel++)
				{
					if (decoder.decodeBit(st.stop) == 1)
					{
						break;
					}

					int i = pixel;
					while (decoder.decodeBit(st.run[i]) == 0)
					{
						block[NATURAL_ORDER[i]] = 0;
						i++;
						pixel++;
					}

					int coefficient = 1;

					if (true)
					{
						coefficient += (int)decoder.decodeExpGolomb(0, st.ac[pixel], st.ac2[pixel]);
					}
					else
					{
						i = 0;
						CabacContext[] ctx = st.ac[pixel / 5];
						while (decoder.decodeBit(ctx[i]) == 0)
						{
							coefficient++;
							i++;
						}
					}

					block[NATURAL_ORDER[pixel]] = decoder.decodeBit(st.acsign) == 1 ? -coefficient : coefficient;
				}
			}
		}
	}


	private int encodeZigZag32(int aSigned)
	{
		return (aSigned << 1) ^ (aSigned >> 31);
	}


	private int decodeZigZag32(int aSigned)
	{
		return (aSigned >>> 1) ^ -(aSigned & 1);
	}


	private static int[][][] readImageData() throws IOException
	{
		int[][][] coefficients = new int[81 * 56][6][8 * 8];

		try (DataInputStream dis = new DataInputStream(new InflaterInputStream(TestAll.class.getResourceAsStream("swallowtail.jpg.data"))))
		{
			for (int[][] mcu : coefficients)
			{
				for (int[] block : mcu)
				{
					for (int pixel = 0; pixel < block.length; pixel++)
					{
						block[pixel] = dis.readShort();

//						System.out.printf("%5d", block[pixel]);
					}
//					System.out.println();
				}
			}
		}

		return coefficients;
	}


	public static void main(String... args)
	{
		try
		{
			int[][][] coefficients = readImageData();
			new TestJPEGX(coefficients);
		}
		catch (Throwable e)
		{
			e.printStackTrace(System.out);
		}
	}
}
