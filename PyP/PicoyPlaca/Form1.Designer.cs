namespace PicoyPlaca
{
    partial class Form1
    {
        /// <summary>
        /// Variable del diseñador necesaria.
        /// </summary>
        private System.ComponentModel.IContainer components = null;

        /// <summary>
        /// Limpiar los recursos que se estén usando.
        /// </summary>
        /// <param name="disposing">true si los recursos administrados se deben desechar; false en caso contrario.</param>
        protected override void Dispose(bool disposing)
        {
            if (disposing && (components != null))
            {
                components.Dispose();
            }
            base.Dispose(disposing);
        }

        #region Código generado por el Diseñador de Windows Forms

        /// <summary>
        /// Método necesario para admitir el Diseñador. No se puede modificar
        /// el contenido de este método con el editor de código.
        /// </summary>
        private void InitializeComponent()
        {
            this.components = new System.ComponentModel.Container();
            this.tbxPlaca = new System.Windows.Forms.TextBox();
            this.label1 = new System.Windows.Forms.Label();
            this.label2 = new System.Windows.Forms.Label();
            this.label3 = new System.Windows.Forms.Label();
            this.Verify = new System.Windows.Forms.Button();
            this.dateTimePicker1 = new System.Windows.Forms.DateTimePicker();
            this.nHour = new System.Windows.Forms.NumericUpDown();
            this.nMinutes = new System.Windows.Forms.NumericUpDown();
            this.toolTip1 = new System.Windows.Forms.ToolTip(this.components);
            ((System.ComponentModel.ISupportInitialize)(this.nHour)).BeginInit();
            ((System.ComponentModel.ISupportInitialize)(this.nMinutes)).BeginInit();
            this.SuspendLayout();
            // 
            // tbxPlaca
            // 
            this.tbxPlaca.Location = new System.Drawing.Point(91, 37);
            this.tbxPlaca.Name = "tbxPlaca";
            this.tbxPlaca.Size = new System.Drawing.Size(100, 20);
            this.tbxPlaca.TabIndex = 0;
            this.toolTip1.SetToolTip(this.tbxPlaca, "XXX0000");
            this.tbxPlaca.TextChanged += new System.EventHandler(this.tbxPlaca_TextChanged);
            // 
            // label1
            // 
            this.label1.AutoSize = true;
            this.label1.Location = new System.Drawing.Point(30, 40);
            this.label1.Name = "label1";
            this.label1.Size = new System.Drawing.Size(34, 13);
            this.label1.TabIndex = 1;
            this.label1.Text = "Placa";
            // 
            // label2
            // 
            this.label2.AutoSize = true;
            this.label2.Location = new System.Drawing.Point(30, 84);
            this.label2.Name = "label2";
            this.label2.Size = new System.Drawing.Size(42, 13);
            this.label2.TabIndex = 3;
            this.label2.Text = "Tiempo";
            // 
            // label3
            // 
            this.label3.AutoSize = true;
            this.label3.Location = new System.Drawing.Point(30, 127);
            this.label3.Name = "label3";
            this.label3.Size = new System.Drawing.Size(37, 13);
            this.label3.TabIndex = 5;
            this.label3.Text = "Fecha";
            // 
            // Verify
            // 
            this.Verify.Location = new System.Drawing.Point(33, 164);
            this.Verify.Name = "Verify";
            this.Verify.Size = new System.Drawing.Size(94, 29);
            this.Verify.TabIndex = 6;
            this.Verify.Text = "Verify";
            this.Verify.UseVisualStyleBackColor = true;
            this.Verify.Click += new System.EventHandler(this.button1_Click);
            // 
            // dateTimePicker1
            // 
            this.dateTimePicker1.Format = System.Windows.Forms.DateTimePickerFormat.Short;
            this.dateTimePicker1.Location = new System.Drawing.Point(91, 127);
            this.dateTimePicker1.Name = "dateTimePicker1";
            this.dateTimePicker1.Size = new System.Drawing.Size(100, 20);
            this.dateTimePicker1.TabIndex = 7;
            // 
            // nHour
            // 
            this.nHour.Location = new System.Drawing.Point(91, 84);
            this.nHour.Maximum = new decimal(new int[] {
            23,
            0,
            0,
            0});
            this.nHour.Minimum = new decimal(new int[] {
            1,
            0,
            0,
            0});
            this.nHour.Name = "nHour";
            this.nHour.Size = new System.Drawing.Size(36, 20);
            this.nHour.TabIndex = 8;
            this.nHour.Value = new decimal(new int[] {
            1,
            0,
            0,
            0});
            // 
            // nMinutes
            // 
            this.nMinutes.Location = new System.Drawing.Point(133, 84);
            this.nMinutes.Maximum = new decimal(new int[] {
            59,
            0,
            0,
            0});
            this.nMinutes.Name = "nMinutes";
            this.nMinutes.Size = new System.Drawing.Size(36, 20);
            this.nMinutes.TabIndex = 9;
            this.nMinutes.Value = new decimal(new int[] {
            1,
            0,
            0,
            0});
            // 
            // Form1
            // 
            this.AutoScaleDimensions = new System.Drawing.SizeF(6F, 13F);
            this.AutoScaleMode = System.Windows.Forms.AutoScaleMode.Font;
            this.ClientSize = new System.Drawing.Size(305, 242);
            this.Controls.Add(this.nMinutes);
            this.Controls.Add(this.nHour);
            this.Controls.Add(this.dateTimePicker1);
            this.Controls.Add(this.Verify);
            this.Controls.Add(this.label3);
            this.Controls.Add(this.label2);
            this.Controls.Add(this.label1);
            this.Controls.Add(this.tbxPlaca);
            this.Name = "Form1";
            this.Text = "PicoyPlaca";
            ((System.ComponentModel.ISupportInitialize)(this.nHour)).EndInit();
            ((System.ComponentModel.ISupportInitialize)(this.nMinutes)).EndInit();
            this.ResumeLayout(false);
            this.PerformLayout();

        }

        #endregion

        private System.Windows.Forms.TextBox tbxPlaca;
        private System.Windows.Forms.Label label1;
        private System.Windows.Forms.Label label2;
        private System.Windows.Forms.Label label3;
        private System.Windows.Forms.Button Verify;
        private System.Windows.Forms.DateTimePicker dateTimePicker1;
        private System.Windows.Forms.NumericUpDown nHour;
        private System.Windows.Forms.NumericUpDown nMinutes;
        private System.Windows.Forms.ToolTip toolTip1;
    }
}

