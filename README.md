# uikit

A clojure-objc library designed to create and manage uikit interfaces.

## Lein

	[uikit "0.1.0"]

## Usage

	(:require [uikit.core :as uikit])

### View vector structure

	[[:classname|instance] name-tag {properties..} children?]

### Define a view
   
	(def view 
          [:UIView :main {:setBackgroundColor ($ ($ UIColor) :redColor)}
            [:UIButton :login {:setTitle:forState ["Login" 0]}]
            [:UIButton :cancel {:setTitle:forState ["Cancel" 0]}]])
   	       

### Children

The children can be passed directly (like aboce) or as a a seq.

    [:UIView :main {:setBackgroundColor ($ ($ UIColor) :redColor)}
       (for [i (range 10)]
        [:UILabel (keyword (str id i)) {:setText (str "Label" i)}])]

### Create a uiviewcontroller
   
	(uikit/controller "Title" view)

### The uikit scope

For every view uikit creates a scope, that's a simple map with [tag-name -> instance]. 
From the sample above you can get the login button from the scope:

     (:login scope)

The scope also contains a :state atom.

    (swap! (:state scope) assoc :custom "data")

### Constraints

uikit gives you direct access to AutoLayout. You can put a :constraints property on any view.
To learn more about AutoLayouts and the visual format: https://developer.apple.com/library/ios/documentation/UserExperience/Conceptual/AutolayoutPG/VisualFormatLanguage/VisualFormatLanguage.html

      (def login
        [:UIView :main
   	  {:setBackgroundColor white
    	    :constraints ["V:[user(50)]-[pass(50)]"
                          "V:[login(100)]|"
                          "C:user.bottom=main.centery 1 -30"
                          "H:|-[user]-|"
                          "H:|-[pass]-|"
                          "H:|[login]|"]}
	  [:UITextField :user {}]
          [:UITextField :pass {}]
          [(uikit/button 1) :login
            {:setTitle:forState ["Login!" 0]}]])

### the custom constraint "C:"

#### Format

	C:{name}.[left|right|top|bottom|leading|trailing|width|height|centerx|centery|baseline][=|<=|>=]{name}.[left|right|top|bottom|leading|trailing|width|height|centerx|centery|baseline][=|<=|>=] multiplier? offset?

#### Examples

     "C:login.top=main.centery"
     "C:login.top=main.centery 0.5"
     "C:login.bottom=main.bottom 1 -10" ;; login.bottom = main.bottom * 1 + (-10)

### Gestures
   
   ;; (uikit/button 1) -> ($ ($ UIButton) :buttonWithType 1)
   [(uikit/button 1) :login   
    {:setTitle:forState ["Login!" 0]
     :gestures {:UITapGestureRecognizer (fn [scope] ...)}}]

   ;; :gestures can take a map or a vector if you need to add the same gesture type multiple times.
   :gestures [:UITapGestureRecognizer (fn [scope] ...)
   	      ;; they also support a map for custom properties
   	      :UITapGestureRecognizer {:setNumberOfTapsRequired 3
	      			       :handler (fn [scope] ...)}]

### Events

Just like :gestures, :events can be a map or a vector. 

    [:UITextField :user
      {:setTextAlignment 1
       :events {:UITextFieldTextDidChangeNotification (fn [scope] ...)}}]

### Navigation utils (only when your top controller is a UINavigationController)

    (uikit/nav-push controller)
    (uikit/nav-pop)
    (uikit/nav-top-controller) ;; gets the current controller

### Alert

	(uikit/alert "Title" "message!")

## License

Copyright © 2014 Gal Dolber

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.