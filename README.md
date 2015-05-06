# uikit

A clojure-objc library designed to create and manage uikit interfaces.

[More docs](https://rawgithub.com/galdolber/uikit/master/docs/uberdoc.html)

## Lein

	[![Clojars Project](http://clojars.org/uikit/latest-version.svg)](http://clojars.org/uikit)

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

The children can be passed directly (like above) or as a a seq.

    [:UIView :main {:setBackgroundColor ($ ($ UIColor) :redColor)}
       (for [i (range 10)]
        [:UILabel (keyword (str id i)) {:setText (str "Label" i)}])]

### Create a uiviewcontroller
   
	(uikit/controller "Title" view)

### The uikit scope

For every view, uikit creates a scope, that is a simple map with all [tag-name -> instance]. 
From the example above you can get the login button from the scope with:

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

C:{name}.[left|right|top|bottom|leading|trailing|width|height|centerx|centery|baseline][=|<=|>=]{name}.[left|right|top|bottom|leading|trailing|width|height|centerx|centery|baseline] multiplier? offset?

#### Examples

     "C:login.top=main.centery"
     "C:login.top=main.centery 0.5"
     "C:login.bottom=main.bottom 1 -10" ;; login.bottom = main.bottom * 1 + (-10)
     "C:login.height=nil.nil 1 30" ;; sets the height to 30

#### Constraints and the scope

All constraints are automatically added to the scope. Custom constraints are named with the first item and first property. For example: "C:login.top=main.centery" will be named :login-top on the scope. 
For every "V:" and "H:" the api creates more than one constraint, but the same rule applies. If you use the visual format you can check your scope for the generated constraints.

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
