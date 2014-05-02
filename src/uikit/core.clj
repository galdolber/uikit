(ns uikit.core)

(defn nsdictionary [map]
  (when map
    (let [d ($ ($ NSMutableDictionary) :new)]
      (doseq [[k v] map]
        ($ d :setObject v :forKey (name k)))
      ($ d :autorelease))))

(def default-center ($ ($ NSNotificationCenter) :defaultCenter))

(defn add-observer
  "Adds an observer for a notification.

  The handler is retained. "
  ([target handler n] (add-observer target handler "invokeWithId:" n))
  ([target handler selector n]
     ($ default-center
        :addObserver ($ handler :retain)
        :selector (sel selector)
        :name (name n)
        :object target)))

(defn remove-observer
  "Removes an observer from the default notification center.

  Releases the handler"
  [handler]
  ($ default-center :removeObserver handler)
  ($ handler :autorelease))

(defn post-notification
  "Post a notification to an object"
  ([target n] (post-notification target n nil))
  ([target n info]
     ($ default-center
        :postNotificationName (name n)
        :object target
        :userInfo (nsdictionary info))))

(def constraint-regex #"C:(\w*)\.(\w*)(=|<=|>=)(\w*)\.(\w*) ?(-?\w*\.?\w*) ?(-?\w*\.?\w*)")

(defn map-invert [m]
  (reduce (fn [m [k v]] (assoc m v k)) {} m))

(def layout-constraints
  {:<=      -1
   :=        0
   :>=       1
   :left     1
   :right    2
   :top      3
   :bottom   4
   :leading  5
   :trailing 6
   :width    7
   :height   8
   :centerx  9
   :centery  10
   :baseline 11
   :nil 0})

(def rlayout-constraints (map-invert layout-constraints))

(defn not-found [c]
  (throw (Exception. (str "Constraint not found " c))))

(defn resolve-constraint [c]
  (if (number? c)
    (if (some #{c} (vals layout-constraints)) c (not-found c))
    (if-let [c (layout-constraints (keyword (name c)))] c (not-found c))))

(defn parse-constraint [^String c]
  (if-not (re-find #"^C:" c)
    c
    (if-let [[f1 p1 e f2 p2 m c] (next (re-find constraint-regex c))]
      [(str f1 "-" p1) f1 (resolve-constraint p1) (resolve-constraint e) f2
       (resolve-constraint p2) (if-not (empty? m) (read-string m) 1.0)
       (if-not (empty? c) (read-string c) 0.0)]
      (throw (Exception. (str "Invalid custom constraint: '" c "'.
Use format: C:{name}.[left|right|top|bottom|leading|trailing|width|height|centerx|centery|baseline][=|<=|>=]{name}.[left|right|top|bottom|leading|trailing|width|height|centerx|centery|baseline][=|<=|>=] multiplier? offset?"))))))

(defn autolayout
  "Creates NSLayoutConstraints from the constraints definitions"
  [ui views c]
  (if (string? c)
    (let [c ($ ($ NSLayoutConstraint) :constraintsWithVisualFormat c
               :options 0 :metrics 0
               :views views)]
      ($ ui :addConstraints c)
      c)
    (let [[_ a b c d e f g] c]
      (let [c ($ ($ NSLayoutConstraint)
                 :constraintWithItem ($ views :objectForKey a)
                 :attribute b
                 :relatedBy c
                 :toItem (if (= d "nil") nil ($ views :objectForKey d))
                 :attribute e
                 :multiplier f
                 :constant g)]
        ($ ui :addConstraint c)
        c))))

(defn set-property
  "Sets a property using objc selectors.

  Accepts multiple args selectors: :setTitle:forState [\"Hello\" 0]"
  [view [k v]]
  (let [[view k]
        (if-not (vector? k) [view k]
                (loop [view view [f & other] k]
                  (if-not other
                    [view f]
                    (recur ((sel (name f)) view) other))))]
    (when-not (#{:parent-constraints :constraints :events :gestures} k)
      (if (and (vector? v) (or (empty? v) (re-find #":" (name k))))
        (apply (partial (sel (name k)) view) v)
        ((sel (name k)) view v)))))

(defn create-scope
  "Creates a scope for a view"
  ([] (create-scope {}))
  ([s] (atom {:observers (atom [])
              :state (atom s)
              :retains (atom [])})))

(defn get-children [children]
  (if (and (= 1 (count children))
           (not (vector? (first children))))
    (first children)
    children))

(defn collect-constraints [[type tag props & children]]
  (into (:constraints props)
        (loop [c [] [f & o] (get-children children)]
          (if f
            (recur (if-let [p (:parent-constraints (nth f 2))]
                     (conj c p)
                     c) o)
            (flatten c)))))

(defn create-ui
  "Instantiates a ui from clojure data."
  ([v] (create-ui (create-scope) v))
  ([scope props]
     (create-ui scope (atom {}) props))
  ([scope rscope [clazz tag props & children :as node]]
     (let [view (if (keyword? clazz) ($ (objc-class (symbol (name clazz))) :new)
                    (do ($ clazz :retain) clazz))
           views (if-let [views (:views @scope)]
                   views
                   (let [views ($ ($ NSMutableDictionary) :new)]
                     (swap! scope assoc :views views)
                      views))]
       ($ views :setValue view :forKey (name tag))
       (swap! scope assoc tag view)
       (swap! rscope assoc view tag)
       (swap! (:retains @scope) conj view)

        (doseq [p (if (map? props) props (partition 2 props))]
          (set-property view p))

        (doseq [c (get-children children)]
          (let [s (create-ui scope rscope c)]
            ($ s :setTranslatesAutoresizingMaskIntoConstraints false)
            ($ view :addSubview s)))

        (let [allc (map parse-constraint (collect-constraints node))]
          (when-not (empty? allc)
            (doseq [c allc]
              (if (string? c)
                (let [l (autolayout view views c)]
                  (when-let [cc ($ l :count)] ;; make it safe for the jvm
                    (doseq [n (range cc)]
                      (let [i ($ l :objectAtIndex n)
                            item1 (@rscope ($ i :firstItem))
                            attr1 (rlayout-constraints ($ i :firstAttribute))]
                        (swap! scope assoc (keyword (str (name item1) "-" (name attr1))) i)
                        (when-let [sec ($ i :secondItem)]
                          (let [item2 (@rscope sec)
                                attr2 (rlayout-constraints ($ i :secondAttribute))]
                            (swap! scope assoc (keyword (str (name item2) "-" (name attr2))) i)))))))
                (swap! scope assoc (keyword (first c)) (autolayout view views c))))))

        (doseq [[k v] (let [g (:gestures props)]
                        (if (map? g) g (partition 2 g)))]
          (let [handler (if (map? v) (:handler v) v)
                selector (sel "invoke")
                handler #(handler @scope)
                g ($ ($ (objc-class (name k)) :alloc)
                     :initWithTarget handler :action selector)]
            ($ handler :retain)
            (swap! (:retains @scope) conj handler g)
            (when (map? v)
              (doseq [p (dissoc v :handler)]
                (set-property g p)))
            ($ view :addGestureRecognizer g)))

        (doseq [[k v] (let [g (:events props)]
                        (if (map? g) g (partition 2 g)))]
          (let [handler #(v (assoc @scope :event %))
                method "invokeWithId:"]
            (if (keyword? k)
              (let [kname (name k)]
                (swap! (:observers @scope) conj handler)
                (add-observer view handler method kname))
              (do
                ($ handler :retain)
                (swap! (:retains @scope) conj handler)
                ($ view :addTarget handler :action (sel method)
                   :forControlEvents k)))))
        view)))

(defn key-window
  "Gets the key window"
  []
  (-> ($ UIApplication)
      ($ :sharedApplication)
      ($ :keyWindow)))

(def current-top-controller (atom nil))

(defn top-controller
  "Gets the top controller"
  []
  ($ (key-window) :rootViewController))

(defn top-view
  "Gets the top view"
  []
  ($ @current-top-controller :view))

(defn setup-keyboard
  "Setups global observers for UIKeyboardWillShowNotification and UIKeyboardWillHideNotification"
  [keyboard-will-show keyboard-will-hide]
  (add-observer nil keyboard-will-show :UIKeyboardWillShowNotification)
  (add-observer nil keyboard-will-hide :UIKeyboardWillHideNotification))

(defn dealloc
  "Deallocs everything in a uikit scope"
  [scope]
  (doseq [v @(:retains @scope)]
    (post-notification v :dealloc)
    ($ v :release))
  (doseq [v @(:observers @scope)]
    (remove-observer v))
  ($ (:views @scope) :release)
  (reset! scope nil))

;; uikit internal controller implementation
(defnstype UIKitController UIViewController
  ([^:id self :initWith ^:id [view s]]
     (doto ($$ self :init)
       ($ :setView ($ view :retain))
       (objc-set! :scope s)
       (#(post-notification ($ % :view) :init))))
  ([^:id self :scope]
     @(objc-get self :scope))
  ([self :shouldAutorotate]
     (:shouldAutorotate (objc-get self :scope)))
  ([self :viewDidLoad]
     (post-notification ($ self :view) :viewDidLoad)
     ($$ self :viewDidLoad))
  ([self :didReceiveMemoryWarning]
     (post-notification ($ self :view) :didReceiveMemoryWarning)
     ($$ self :didReceiveMemoryWarning))
  ([self :viewDidLayoutSubviews]
     (post-notification ($ self :view) :viewDidLayoutSubviews)
     ($$ self :viewDidLayoutSubviews))
  ([self :viewWillLayoutSubviews]
     (post-notification ($ self :view) :viewWillLayoutSubviews)
     ($$ self :viewWillLayoutSubviews))
  ([self :viewDidAppear animated]
     (post-notification ($ self :view) :viewDidAppear)
     ($$ self :viewDidAppear animated))
  ([self :viewDidDisappear animated]
     (post-notification ($ self :view) :viewDidDisappear)
     ($$ self :viewDidDisappear animated))
  ([self :viewWillAppear animated]
     (post-notification ($ self :view) :viewWillAppear)
     ($$ self :viewWillAppear animated))
  ([self :viewWillDisappear animated]
     (post-notification ($ self :view) :viewWillDisappear)
     ($$ self :viewWillDisappear animated))
  ([self :dealloc]
     (dealloc (objc-get self :scope))
     ($ ($ self :view) :release)
     ($$ self :dealloc)))

(defn controller
  "Creates a uikit controller with a title and a view data"
  ([title view] (controller title view {}))
  ([title view init]
     (let [scope (create-scope init)
           view (create-ui scope (if (fn? view) (view init) view))]
       (doto ($ ($ ($ UIKitController) :alloc) :initWith [view scope])
         ($ :setTitle title)
         ($ :setView view)
         ($ :autorelease)))))

(defn nav-push
  "Pushes a controller into the top navigation controller"
  ([controller] (nav-push controller false))
  ([controller animated] ($ @current-top-controller :pushViewController controller
                            :animated animated)))

(defn nav-pop
  "Pops a controller from the current navigation controller"
  ([] (nav-pop true))
  ([animated]
     ($ @current-top-controller :popViewControllerAnimated animated)))

(defn nav-top-controller
  "Gets the top controller from the current navigation controller"
  ([] (nav-top-controller @current-top-controller))
  ([nav] ($ nav :visibleViewController)))

(defn alert!
  "Creates and shows a simple UIAlertView"
  ([title msg] (alert! title msg "Cancel" nil))
  ([title msg cancel] (alert! title msg cancel nil))
  ([title msg cancel delegate]
     (-> ($ UIAlertView)
         ($ :alloc)
         ($ :initWithTitle title
            :message msg
            :delegate delegate
            :cancelButtonTitle cancel
            :otherButtonTitles nil)
         ($ :autorelease)
         ($ :show))))

(defn button
  "Creates a UIButton with a type"
  [type] ($ ($ UIButton) :buttonWithType type))
