Add iutils jar and coxRaidScouter jar and webhook.py to .openosrs/plugins

Additional requirements 
- python, requests module, dhooks module

Must be on fixed Mode for auto/rejoin CC mode.  
Also depends upon "Chambers of Xeric" plugin Raid Layout Message

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