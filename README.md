SWIM ran this on 3 accounts for a little under 3 months(approx 2000 raids) two temps towards the end. However this is a commonly reported activity so would only recommend using on burner accounts. Works nicely if setup on a cloud VM and webhooked to discord. Only need to check once every 6 hours if using the auto leave/rejoin cc feature  

Additional requirements 
- iutils by illumine (illumineawake/illu-plugins)  
- "Chambers of Xeric" plugin Raid Layout Message Toggled ON  

V1.4  
Added Java webhook instead of external python file  
Modified reset to be on logout instead of login due to sometimes leaving cc immediately upon login  

V1.3  
Added 5h login timer handling  
Added reset on login(Prevents webhook posting raid taken on relog)  
Added webhook message on logout  

v1.2 -  
Added Good Crabs detector  
Added ability to input specific rotations rather than just blacklist  
added os detection for python run command  
  
v1.1 -  
Moved webhook.py within plugins folder for ease of setup  
Added layout to webhook (SCSPF e.g)  
Added new embed for webhook   
Added webhook message when raid is taken(user has started scouting again)  