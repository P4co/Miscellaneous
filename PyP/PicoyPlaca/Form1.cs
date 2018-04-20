using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Data;
using System.Drawing;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;
using System.Text.RegularExpressions;
using System.Collections;
using System.Globalization;

namespace PicoyPlaca
{
    public partial class Form1 : Form
    {
        public string InpTime, InputDate;
        public int LastNumber;

        public Form1()
        {
            InitializeComponent();
        }

        public bool IsPlateNumberCorrect( string PlateN)
        {
                
            bool ret = false;
            //Verify the PlateNumber has the format AAA000
            var PlateFormat = new Regex("^[a-zA-Z0-9]*$");
            var StringsFormat = new Regex("^[a-zA-Z]*$");
            var NumbersFormat= new Regex("^[0-9]*$");
            string PlateNumbers = PlateN.Substring(3, (PlateN.Length - 3));
            string PlateLetters = PlateN.Substring(0, 3);

            if (PlateFormat.IsMatch(PlateN) &&
                !StringsFormat.IsMatch(PlateN.Substring(3, 1)) &&
                NumbersFormat.IsMatch(PlateNumbers) &&
                PlateLetters.Length == 3 &&
                PlateNumbers.Length == 4)
            {
                LastNumber = int.Parse(PlateNumbers.Substring(3, 1));
                ret = true;
            }
            else
            {
                MessageBox.Show("Enter the plate number without spaces or special letters \nExample: AAA0000", "Invalid Plate Number");
            }

            return ret;

        }

        static bool QueryPyP( int QLastNum, string QTime, int PHour, int PMinutes)
        {
            bool IsInPyP = false; 
            ArrayList PypRules = new ArrayList()
            {
                new PyPData {
                    Days = "Lunes",
                    LNumbers = new int[2] {1,2},
                },

                new PyPData {
                    Days = "Martes",
                    LNumbers = new int[2] {3,4},
                },

                new PyPData {
                    Days = "Miercoles",
                    LNumbers = new int[2] {5,6},
                },
                new PyPData {
                    Days = "Jueves",
                    LNumbers = new int[2] {7,8},
                },
                new PyPData {
                    Days = "Viernes",
                    LNumbers = new int[2] {9,10},
                },

            };

            var pypEnum = PypRules.OfType<PyPData>();

            var InNumber = from PyPData in pypEnum
                           where ( PyPData.LNumbers.Contains(QLastNum)  &&
                                   ( (PHour <= PyPData.EndHour[0] && PHour >= PyPData.InitHour[0]) || 
                                     (PHour <= PyPData.EndHour[1] && PHour >= PyPData.InitHour[1]) ) &&
                                    PyPData.Days.Equals(QTime, StringComparison.InvariantCultureIgnoreCase) 
                                  )
                           select PyPData;

            //Search in the database the Number and compare with the date and time
            foreach (var PyPData in InNumber)
            {
                IsInPyP = true;
                //Control output
                //Console.WriteLine("{0} Day {1}XXX",PyPData.Days, PyPData.LNumbers);
            }

            return IsInPyP;

        }

        private void tbxPlaca_TextChanged(object sender, EventArgs e)
        {
            if (tbxPlaca.Text.Length == 0)
            {
                tbxPlaca.Text = "Ex: AAA0000";
                tbxPlaca.ForeColor = SystemColors.GrayText;
            }
        }

        private void button1_Click(object sender, EventArgs e)
        {
            try
            {
                if (!tbxPlaca.Text.Equals("") )
                {
                    string PlateNumber = tbxPlaca.Text;
                    string Date = dateTimePicker1.Value.ToString("dddd", new CultureInfo("es-ES"));
                    int Hour = (int)nHour.Value;
                    int Minutes = (int)nMinutes.Value;
                    if (IsPlateNumberCorrect(PlateNumber))
                    {

                        if (QueryPyP(LastNumber, Date, Hour, Minutes))
                        {
                           MessageBox.Show("You may not drive the car is in PYP");
                        }
                        else
                        {
                           MessageBox.Show("The car is not in PYP");
                        }

                    }

                }
                else
                {
                    MessageBox.Show("Datos incorrectos y/o campos vacios.");
                }
            }
            catch (Exception ex)
            {
                throw ex;
            }
       
        }
    }

    class PyPData
    {
        public string Days { get; set; }
        public int[] InitHour { get; set; }
        public int[] InitMinutes { get; set; }
        public int[] EndHour { get; set; }
        public int[] EndMinutes { get; set; }
        public int[] LNumbers { get; set; }


        public PyPData ()
        {
            Days = "NoDay";
            InitHour = new int[2] { 7, 16 };
            InitMinutes = new int[2] { 0, 0 };
            EndHour = new int[2] { 9, 19 };
            EndMinutes = new int[2] { 30, 30 };
            LNumbers = new int[2] { 0, 0 };
            
        }
        
    }

}
