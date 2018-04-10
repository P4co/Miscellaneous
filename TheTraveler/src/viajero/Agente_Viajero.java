package viajero;
//https://gist.github.com/guilleiguaran/659268
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.*;

public class Agente_Viajero extends JFrame implements Runnable
{
	Container contenedorHormigas;
	Mapa mapaHormigas;
	JList RutaHormigas;
	Vector camHormigas;
	JButton bt_comenzarHormigas,bt_caminoHormigas;
	JList listHormigas;
        JLabel membreteEstudiante1;
        JLabel membreteEstudiante2;
        JLabel membreteEstudiante3;
	JScrollPane paneHormigas;
	JComboBox comboInicioRuta;
	int nodo_inicialHormigas,caminoHormigas=-1;
	float[][] matHormigas;
	Tablero tabHormigas;
	Thread hiloHormigas;
	Agente_Viajero yoHormigas;
	
	
	Agente_Viajero()
	{
	  this.setTitle("EL PROBLEMA DEL VIAJERO");
	  contenedorHormigas = this.getContentPane();
	  contenedorHormigas.setLayout(null);
	  yoHormigas=this;

          membreteEstudiante1 = new JLabel();
          membreteEstudiante1.setBounds(550, 0, 200,100);
	  membreteEstudiante1.setText("UNIVERSIDAD SEK");
          membreteEstudiante1.setBackground(Color.BLACK);
          contenedorHormigas.add(membreteEstudiante1);
          
          membreteEstudiante2 = new JLabel();
          membreteEstudiante2.setBounds(520, 20, 200,100);
	  membreteEstudiante2.setText("TECNOLOGÍAS EMERGENTES");
          membreteEstudiante2.setBackground(Color.BLACK);
          contenedorHormigas.add(membreteEstudiante2);
          
          membreteEstudiante3 = new JLabel();
          membreteEstudiante3.setBounds(550, 40, 200,100);
	  membreteEstudiante3.setText("Grupo 3");
          membreteEstudiante3.setBackground(Color.BLACK);
          contenedorHormigas.add(membreteEstudiante3);
          
          
	  RutaHormigas = new JList();
	  camHormigas= new Vector();	
	  tabHormigas = new Tablero();
	  
	  mapaHormigas = new Mapa();
	  mapaHormigas.setBounds(400,100,400,400);
	  contenedorHormigas.add(mapaHormigas);
	  
	  bt_comenzarHormigas = new JButton("Aceptar");
	  bt_caminoHormigas   = new JButton("Generar");
	  bt_comenzarHormigas.addActionListener(new ActionListener()
	  {
	  	public void actionPerformed (ActionEvent e)
	  	{
                    String cad="";
	  	    camHormigas.clear();
	  	    int tam=getCamHormigas();
	  	    String vec[]= new String[tam];
	  	      for (int i=0;i<tam;i++) 
	  	      {
	  	      	vec[i]=""+(i+1);
	  	      	comboInicioRuta.addItem(vec[i]);
	  	      	
	  	      	
			  }
			  
	  		
	  	
	  		listHormigas.updateUI();
		
	  		listHormigas.setListData(camHormigas);
	  		paneHormigas.updateUI();
	  		bt_comenzarHormigas.setVisible(false);
	  		bt_caminoHormigas.setVisible(true);
	  		MatrizHormigas(tam);
	  		tabHormigas.setMatriz(matHormigas);
	  		
	  	}
	  });
	  listHormigas  = new JList();
	  paneHormigas  = new JScrollPane(listHormigas);
	  comboInicioRuta = new JComboBox();
	  comboInicioRuta.setBounds(800,300,100,20);
	  
	  bt_comenzarHormigas.setBounds(400,500,100,20);
	  bt_caminoHormigas.setBounds(400,500,100,20);
	  bt_caminoHormigas.setVisible(false);
	  paneHormigas.setBounds(800,100,100,200);
	  tabHormigas.setBounds(500,500,400,200);
	  contenedorHormigas.add(tabHormigas);
	  contenedorHormigas.add(comboInicioRuta);
	  contenedorHormigas.add(bt_comenzarHormigas);
	  contenedorHormigas.add(bt_caminoHormigas);
	  contenedorHormigas.add(paneHormigas);
	  
	  bt_caminoHormigas.addActionListener(new ActionListener()
	  {
	  	public void actionPerformed (ActionEvent e)
	  	{
	  		int tam=getCamHormigas();
	  		String temp="";
	  		nodo_inicialHormigas=comboInicioRuta.getSelectedIndex()+1;
	  		camHormigas.clear();
	  		double a =System.currentTimeMillis();
	  		SigHormigas(comboInicioRuta.getSelectedIndex(),tam,0,temp);
	  		double c =System.currentTimeMillis()-a;
	  		JOptionPane.showMessageDialog(yoHormigas,c,"",1);
	  		listHormigas.updateUI();
	  		hiloHormigas = new Thread(yoHormigas);
	  		hiloHormigas.start();
	  	
	  	}
	  });
	  
	}
	
    public int getCamHormigas()
	{
		return mapaHormigas.getCaminoHormiga();
	}
	
	public void run()
	{
		
	int i=0;float menor=getValcadHormigas(""+camHormigas.elementAt(0)),m=0;
	boolean sw=true;
	
		while (i<camHormigas.size())
		{
			try 
			{
				String cad="";
				
				
				
						cad=""+camHormigas.elementAt(i);
						m=getValcadHormigas(cad);
						System.out.println (cad.substring(0,1) +" "+(i+1)+" "+m);
						mostrarHormigas(cad);
						Thread.sleep(10);
					
							if (m<=menor)
							{
							caminoHormigas=i;
							menor=m;
							System.out.println (" MENOR " +menor);
							}

				
					
					i++;
			
			
				  
		    }
		    catch (Exception ex) 
		    {
		    	
		    }
		}
		
		mostrarHormigas(""+camHormigas.elementAt(caminoHormigas));	
	 JOptionPane.showMessageDialog(tabHormigas,menor+" en el camino "+"\n"+camHormigas.elementAt(caminoHormigas),"Mejor Ruta",1);
	 
	
	}

public void SigHormigas(int i, int n, int p, String acum)
{

    if (p<n && i< n)// si se acabo un para o un contador
    {
    	boolean no=false;
      	
    	for(int k=0;k<acum.length()&&!no;k++)//si ya esta el numero de lo vuelo
        {
            if(acum.substring(k,k+1).equals(""+(i+1))) no=true;
        }
        
        if(!no) SigHormigas(0,n,p+1,acum+""+(i+1));
        SigHormigas(i+1,n,p,acum);
        no=false;
        for(int k=0;k<acum.length() && !no; k++)
        {
            if(acum.substring(k,k+1) .equals(""+(i+1))) no=true;
        }
        if(!no && acum.length()==n-1)
        {
        	acum=acum+""+(i+1)+nodo_inicialHormigas;
        	if(acum.startsWith(""+nodo_inicialHormigas))
             camHormigas.add(acum);
             else
              return ;
            
        }
        
       
    	
    }
}


public float getDistanciaHormigas(String i, String j)
{
	
	int a,b,c,d;
	float r,r1;
	a =mapaHormigas.getaHormiga(Integer.parseInt(i));
	b =mapaHormigas.getaHormiga(Integer.parseInt(j));
	c =mapaHormigas.getbHormiga(Integer.parseInt(i));
	d =mapaHormigas.getbHormiga(Integer.parseInt(j));
    r=(float)Math.pow(b-a,2);r1=(float)Math.pow(d-c,2);
    
	return (float)Math.sqrt((r+r1));
}	

public void MatrizHormigas(int n)
{
	matHormigas = new float[n][n];
	
    for (int i=0;i<n;i++)
    {  
    	for(int j=0;j<n;j++)
    	{
    	    matHormigas[i][j]=getDistanciaHormigas(""+i,""+j);
    	    
    	}
    	
    } 	
}

public float getValcadHormigas(String cadena)
{
	float num=0;
	int a=0,b=0;
   for (int i=0;i<cadena.length()-1;i++) 
   {
   	  a=Integer.parseInt(cadena.substring(i,i+1));
   	  b=Integer.parseInt(cadena.substring(i+1,i+2));
   	  num=num+matHormigas[a-1][b-1];
   	 
   	  
   }
  
   return num;	
}

public  float getTotalHormigas()
{
	String cad="";
  float menor=getValcadHormigas(""+camHormigas.elementAt(0)),m=0;

	 for (int i=0;i<camHormigas.size();i++)
	 {  
	    cad=""+camHormigas.elementAt(i);
	    m=getValcadHormigas(cad);
	    System.out.println (cad.substring(0,1) +" "+(i+1)+" "+m);
	 	if (cad.startsWith(""+nodo_inicialHormigas))
	 	{
			
			
	 		if (m<=menor)
	 		{
	 			caminoHormigas=i;
	 			menor=m;
	 			System.out.println (" MENOR " +menor);
	 		}
	 	}else
	 	return menor;
	 	
	 
	 }
	 
	 return menor;
}

public void mostrarHormigas (String camino)
{
	mapaHormigas.setResultadoHormiga(camino);
}
	
public static void main (String arg[])
	{
	   Agente_Viajero frame = new Agente_Viajero();
	   Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
	   frame.setUndecorated(!true);
	   frame.setSize(size.width,size.height);
	   frame.setVisible(true);	
	}
}