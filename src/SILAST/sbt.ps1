#   i f   a   l o c a l   s b t - l a n u c h . j a r   e x i s t s ,   u s e   t h a t   o n e  
 $ s b t _ j a r   =   " s b t - l a u n c h . j a r " ;  
 $ s b t _ p a t h   =   " $ ( G e t - I t e m   . ) \ $ s b t _ j a r " ;  
  
 $ s d   =   S p l i t - P a t h   ( G e t - V a r i a b l e   M y I n v o c a t i o n   - S c o p e   0 ) . V a l u e . M y C o m m a n d . P a t h ;  
  
 i f ( T e s t - P a t h   $ s b t _ p a t h ) {  
         # a l r e a d y   a s s i g n e d  
 }   e l s e i f ( ( T e s t - P a t h   V a r i a b l e : \ S B T _ L A U N C H _ J A R )   - a n d   ( T e s t - P a t h   $ S B T _ L A U N C H _ J A R ) )   {  
         $ s b t _ p a t h   =   $ S B T _ L A U N C H _ J A R ;  
 }   e l s e i f ( ( T e s t - P a t h   " $ s d \ $ s b t _ j a r " ) )   {  
         $ s b t _ p a t h   =   " $ s d \ $ s b t _ j a r " ;  
 }   e l s e i f ( ( T e s t - P a t h   E n v : \ S B T _ L A U N C H _ J A R )   - a n d     ( T e s t - P a t h   " $ ( E n v : \ S B T _ L A U N C H _ J A R ) " ) )   {  
         $ s b t _ p a t h   =   $ E n v : S B T _ L A U N C H _ J A R ;  
 }   #   e l s e ,   j u s t   h o p e   t h a t   s b t _ l a u n c h . j a r   i s   i n   P A T H  
 j a v a   - X m x 5 1 2 M   - j a r   $ s b t _ p a t h   $ a r g s 