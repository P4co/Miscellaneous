package viajero;

import java.awt.*;
import java.applet.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.geom.*;
import java.util.*;

class Mapa extends JPanel
{
	int A,B,b;
	Image im;
	Graphics2D  grafica;
	Point[] puntosHormiga;
	int n_puntosHormiga=0;
	Vector posHormiga,a,b1;
	boolean limiteHormiga=false;
	boolean resultado=false;
	String caminoHormiga;
	
	Mapa()
	{
		this.setBackground(Color.black);
		puntosHormiga = new Point[100];
		posHormiga    = new Vector();
		b1     = new Vector();
		a      = new Vector();
		this.setBorder( BorderFactory.createBevelBorder(1));
		
	  this.addMouseMotionListener(new MouseMotionAdapter()
	   {
	   	public void mouseMoved(MouseEvent e)
	   	{
	   	
	   		A=e.getX()/20;
	   		b=e.getY();
	   		
	   		B= ((b-getHeight())*-1);
	   	
	   		repaint();
	   		
	   	}
	   	
	   	
	   });
	   
	   this.addMouseListener(new MouseAdapter()
	   {
	   	 public void mouseClicked (MouseEvent e)
	   	 { 	
	   	  if(!limiteHormiga)
	   	  {
	   	  	if (!((A==24)||(B/20==24)))
	   	  	{
	   	  	
	   	  
	   	    puntosHormiga[n_puntosHormiga++]=e.getPoint();
	   	    posHormiga.add("("+e.getX()/20+","+B/20+")");
	   	    a.add(e.getX()/20);b1.add(B/20);
	   	    repaint();
	   	    if(n_puntosHormiga==20)
	   	    {
	   	    	limiteHormiga=true;
	   	    	JOptionPane.showMessageDialog(null,"LLegaste al limite de nodos","Info",JOptionPane.INFORMATION_MESSAGE);
	   	    }
	   	    
	   	    }
	   	  }    
	   	 }
	   	 
	   });	
	}
	
	public void update(Graphics g)
	{
		paint(g);
	}
	
		
	public void paintComponent(Graphics g)
	{
		im = this.createImage(this.getWidth(),this.getWidth());
		grafica =(Graphics2D)im.getGraphics(); 
		pintarHormiga(grafica);
		g.drawImage(im,0,0,this.getWidth(),this.getWidth(),this);
	}
	
	public void pintarHormiga(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); //para mejorar la calidad de la pintada	
		g.setColor(Color.white);
		g.fillRect(0,0,getWidth(),getHeight() );
		g.setColor(Color.LIGHT_GRAY);
	    for(int i=0;i<100;i+=4)
		{
		    g.drawLine(5*i,500,5*i,0);
		    g.drawLine(0,5*i,500,5*i);
		   
		}    	
		g.setColor(Color.red);
		g.setFont(new Font("Arial", Font.BOLD,12));
		g.drawString("("+A+","+B/20+")",A*20 +2,b);
		
		
		if (this.resultado)
		  this.resultadoHormiga(g);
		  	
		for (int i=0;i<n_puntosHormiga;i++)
		{
			g.setColor(Color.BLUE);	
			g.fillOval(puntosHormiga[i].x-8,puntosHormiga[i].y-8,18,18);
			g.setColor(Color.white);
			g.drawString(""+(i+1),(puntosHormiga[i].x-10) +9/2,(puntosHormiga[i].y-9)+15);
			g.setColor(Color.black);
			g.drawString(""+posHormiga.elementAt(i),puntosHormiga[i].x-9,puntosHormiga[i].y-18);
		}
		
		
		
			
		
	}
	public int getCaminoHormiga()
	{
	 return this.n_puntosHormiga;
	}
	
	
	public int getaHormiga(int n)
	{
		
		return Integer.parseInt(""+a.elementAt(n));
	}
	
	public int getbHormiga(int n)
	{
		return Integer.parseInt(""+b1.elementAt(n));
	}
	
	public void setResultadoHormiga(String camino)
	{
		this.resultado=true;
		this.caminoHormiga=camino;
		repaint();
	
		
		
	}
	
	public void resultadoHormiga(Graphics2D g )
	{
		int temp,temp2;
		g.setColor(Color.red);
		for(int i=0;i<caminoHormiga.length()-1;i++)
		{
			temp=Integer.parseInt(""+caminoHormiga.substring(i,i+1));
			temp2=Integer.parseInt(""+caminoHormiga.substring(i+1,i+2));
			g.drawLine(puntosHormiga[temp-1].x,puntosHormiga[temp-1].y,puntosHormiga[temp2-1].x,puntosHormiga[temp2-1].y);
			g.drawString(""+(i+1),(int)((((puntosHormiga[temp-1].x+puntosHormiga[temp2-1].x)/2)+puntosHormiga[temp-1].x)/2), (int)((((puntosHormiga[temp-1].y+puntosHormiga[temp2-1].y)/2)+puntosHormiga[temp-1].y)/2));
			
		}
	}


	
}

class Tablero extends JPanel
{
	
    JTextField m[][];
		
	Tablero ()
	{
		
	}
	
	public void setMatriz(float mat[][])
	{
		
		this.removeAll();
		m = new JTextField[mat.length][mat[0].length];
		this.setLayout(new GridLayout(mat.length,mat[0].length) );
		
		for (int i = 0; i<mat.length; i++)
		{  for (int j = 0; j<mat[0].length; j++) 
			{
			  m[i][j]= new JTextField();
			  m[i][j].setEditable(false);
			  m[i][j].setAutoscrolls(false);
			  m[i][j].setFont((new Font("Arial", Font.BOLD,10)));
			  m[i][j].setText(""+mat[i][j]);
			  m[i][j].setCaretPosition(0);
			  add(m[i][j]);	
		    }
		}
		
		this.updateUI();
		
	}

}
